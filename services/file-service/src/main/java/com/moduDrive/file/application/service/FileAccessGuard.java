package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.domain.model.Permission;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.domain.model.ShareScope;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Answers "may this caller act on this file?". The owner ({@code file.ownerId}) holds every
 * permission. A share on a directory is inherited by everything under it, resolved at check time
 * from the file's ancestor directories; nothing is copied onto child rows.
 */
@Component
@RequiredArgsConstructor
class FileAccessGuard {

    private final FindFileSharePort findFileSharePort;
    private final FindFilePort findFilePort;

    /** The caller's role from {@link #access}; null when they own the file or have no access. */
    Role effectiveRole(File file, UUID callerId) {
        if (isOwner(file, callerId)) {
            return null;
        }
        return access(file, callerId).role();
    }

    void requireOwner(File file, UUID callerId) {
        if (!isOwner(file, callerId)) {
            throw accessDenied(file);
        }
    }

    /** Throws unless the caller may {@code required} on this file; returns what the check resolved
     * ({@link Access#NONE} for the owner), so a caller that also shows the role or the grant's
     * "공유한 사용자/날짜" doesn't walk the same path again. */
    Access requirePermission(File file, UUID callerId, Permission required) {
        if (isOwner(file, callerId)) {
            return Access.NONE;
        }
        // A trashed or purged item is gone to a grantee, even one whose grant is inherited from a
        // still-live ancestor.
        if (file.isRemoved()) {
            throw accessDenied(file);
        }
        Access access = access(file, callerId);
        if (access.role() == null || !access.role().permissions().contains(required)) {
            throw accessDenied(file);
        }
        return access;
    }

    /** Carries isDirectory so the client can word "이 파일에" vs "이 폴더에". */
    private static BusinessException accessDenied(File file) {
        return new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED, Map.of("isDirectory", file.isDirectory()));
    }

    /** The caller's access to a file and the named share it comes from. {@code role} is null when
     * they have none; {@code grant} is null when the role comes from a LINK scope rather than a
     * share (or there's no role). */
    record Access(Role role, FileShare grant) {
        static final Access NONE = new Access(null, null);
    }

    /** Priority: a grant on this exact file wins outright, even over a more generous inherited
     * one; otherwise the nearest ancestor's grant; only when no grant exists anywhere on the path,
     * a LINK scope on the file or an ancestor (viewer, as for an anonymous link holder).
     * {@link Access#NONE} when none applies, and for the owner, who holds no share.
     * <p>
     * {@code grant} is the share row behind the role, shown as "공유한 사용자"/"공유된 날짜".
     * Two queries however deep the file sits: its ancestors, then the caller's grants on all of
     * them. */
    Access access(File file, UUID callerId) {
        if (callerId == null) {
            return Access.NONE;
        }
        List<File> ancestors = ancestorDirectories(file);
        List<FileId> onPath = new ArrayList<>();
        onPath.add(new FileId(file.getId()));
        ancestors.forEach(ancestor -> onPath.add(new FileId(ancestor.getId())));
        Map<UUID, FileShare> grants = findFileSharePort.findBySharedWithUserIdAndFileIdIn(callerId, onPath).stream()
                .collect(Collectors.toMap(FileShare::getFileId, share -> share, (a, b) -> a));

        // ancestorDirectories() is root-most first; walk it backwards so the first hit is nearest.
        FileShare grant = grants.get(file.getId());
        for (int i = ancestors.size() - 1; grant == null && i >= 0; i--) {
            grant = grants.get(ancestors.get(i).getId());
        }
        if (grant != null) {
            return new Access(grant.getRole(), grant);
        }
        return new Access(linkRoleFallback(file, ancestors), null);
    }

    /** The link role when {@code file} or one of {@code ancestors} is LINK-scoped, else null.
     * Always VIEWER (see {@link File#enableLinkSharing()}), so which one matched doesn't matter.
     * Shared by {@link #access} and {@code PublicFileResolver}, so both routes agree on "anyone
     * with the link". Takes the ancestors because PublicFileResolver reuses them for its
     * guest-invite check — computing them twice would also widen the timing gap between rejection
     * reasons (#320). */
    Role linkRoleFallback(File file, List<File> ancestors) {
        if (file.getAccessScope() == ShareScope.LINK) {
            return file.getLinkRole();
        }
        for (File ancestor : ancestors) {
            if (ancestor.getAccessScope() == ShareScope.LINK) {
                return ancestor.getLinkRole();
            }
        }
        return null;
    }

    /** The directories on {@code file}'s materialized path, root-most first — one query. */
    List<File> ancestorDirectories(File file) {
        return findFilePort.findActiveAncestorDirectories(new NamespaceId(file.getNamespaceId()), file.getPath());
    }

    private boolean isOwner(File file, UUID callerId) {
        return callerId != null && callerId.equals(file.getOwnerId());
    }
}
