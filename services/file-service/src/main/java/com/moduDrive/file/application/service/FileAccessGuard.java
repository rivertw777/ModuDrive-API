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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The single place that answers "may this caller act on this file?". Ownership is
 * {@code file.ownerId}, checked directly — there is no OWNER role or share row, and the owner
 * implicitly holds every permission.
 * <p>
 * A share on a directory is inherited by everything nested under it (Google-Drive-style): the
 * grant is stored only on the directory, and access to a descendant is resolved at check time by
 * walking the descendant's ancestor directories. Nothing is copied onto child rows.
 */
@Component
@RequiredArgsConstructor
class FileAccessGuard {

    private final FindFileSharePort findFileSharePort;
    private final FindFilePort findFilePort;

    /** The caller's effective role on this file — their own grant on this exact file if they have
     * one (an ancestor's is never consulted then, however more generous it is), otherwise the
     * nearest ancestor directory's grant above it. Null when they own it or have no grant at
     * all. */
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
        // A trashed or purged item is owner-only (restore / purge). To a grantee it's gone — an
        // inherited grant from a still-live ancestor directory must not keep a removed descendant
        // readable or downloadable. The owner short-circuits above, so their own
        // restore/purge/FILE_ALREADY_DELETED paths are untouched.
        if (file.isRemoved()) {
            throw accessDenied(file);
        }
        Access access = access(file, callerId);
        if (access.role() == null || !access.role().permissions().contains(required)) {
            throw accessDenied(file);
        }
        return access;
    }

    /** Attaches isDirectory to the error body — the file already exists (we have it in hand), so
     * this isn't a new existence leak, just enough for the client to word "이 파일에" vs "이
     * 폴더에" instead of guessing. */
    private static BusinessException accessDenied(File file) {
        return new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED, Map.of("isDirectory", file.isDirectory()));
    }

    /** The caller's access to a file and the named share it comes from. {@code role} is null when
     * they have none; {@code grant} is null when the role comes from a LINK scope rather than a
     * share (or there's no role). */
    record Access(Role role, FileShare grant) {
        static final Access NONE = new Access(null, null);
    }

    /** {@link Access#NONE} when the caller has no explicit share on this file or on any directory
     * above it, and neither this file nor any ancestor is LINK-scoped either. A grant on this exact
     * file is authoritative and skips ancestors entirely — it's a deliberate, file-specific decision
     * by the owner, so it overrides an inherited grant even when the inherited one would be more
     * generous. Only when there's no direct grant do ancestors count, nearest-wins: a farther
     * ancestor's more generous role never outranks a nearer ancestor's. A named grant (own or
     * inherited) always outranks a LINK fallback, even a more generous one. Only once no named grant
     * exists anywhere on the path does a self/ancestor LINK scope kick in, granting a signed-in
     * stranger the same viewer-only access an anonymous link-holder already gets through the public
     * routes (issue #303) — there is no reason to make a logged-in visitor use a different,
     * token-bearing URL for the same "anyone with the link" file.
     * <p>
     * {@code grant} is the specific share row behind the role — what a detail panel or a shared
     * folder's listing shows as "공유한 사용자"/"공유된 날짜", so every child of a shared directory
     * attributes to the same one. Owner-blind: an owner holds no share, so it's NONE for them.
     * <p>
     * Two queries however deep the file sits: the folders above it, then the caller's grants on the
     * file and all of them at once (the priority above is applied in memory). */
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

        // ancestorDirectories() returns root-most first; walk it backwards so the first hit is the
        // nearest ancestor, matching ListFileSharesService's own nearest-wins tie-break.
        FileShare grant = grants.get(file.getId());
        for (int i = ancestors.size() - 1; grant == null && i >= 0; i--) {
            grant = grants.get(ancestors.get(i).getId());
        }
        if (grant != null) {
            return new Access(grant.getRole(), grant);
        }
        return new Access(linkRoleFallback(file, ancestors), null);
    }

    /** Last resort once no named grant exists anywhere on the path: this file's own LINK scope,
     * or failing that, the nearest ancestor directory's. Either way the granted role is always
     * VIEWER (link sharing never hands out more, see {@code UpdateFileScopeService}), so which
     * one matched doesn't change the outcome — no nearest-wins tie-break needed here.
     * <p>
     * Whether {@code file} is reachable purely through "anyone with the link", with no grant and
     * no caller identity involved at all. {@link #access} uses this as its own last resort
     * for a signed-in caller with no named grant; {@code PublicFileResolver} uses it directly for
     * an anonymous visitor, who has nothing else to fall back on (issue #303 — the two routes
     * share this one judgment instead of each re-deciding what "anyone with the link" means).
     * Null when neither applies.
     * <p>
     * Takes the caller's own ancestor list rather than computing it internally — the only external
     * caller, {@code PublicFileResolver}, needs that same list right after for its guest-invite
     * check, and computing it twice per anonymous request would both waste a query and widen the
     * timing gap between "no ancestors to check" and "walked several" (issue #320). */
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
