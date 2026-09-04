package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.domain.model.Permission;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    /** The caller's effective role on this file — their own grant or the most generous one
     * inherited from a directory above it. Null when they own it or have no grant at all. */
    Role effectiveRole(File file, UUID callerId) {
        if (isOwner(file, callerId)) {
            return null;
        }
        return resolveRole(file, callerId);
    }

    void requireOwner(File file, UUID callerId) {
        if (!isOwner(file, callerId)) {
            throw new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED);
        }
    }

    void requirePermission(File file, UUID callerId, Permission required) {
        if (isOwner(file, callerId)) {
            return;
        }
        // A trashed item is owner-only (restore / purge). To a grantee it's gone — an inherited
        // grant from a still-live ancestor directory must not keep a soft-deleted descendant
        // readable or downloadable. The owner short-circuits above, so their own
        // restore/purge/FILE_ALREADY_DELETED paths are untouched.
        if (file.getStatus() == FileStatus.DELETED) {
            throw new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED);
        }
        Role granted = resolveRole(file, callerId);
        if (granted == null || !granted.permissions().contains(required)) {
            throw new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED);
        }
    }

    /** Returns null when the caller has no explicit share on this file or on any directory above
     * it. A LINK file's {@code linkRole} is deliberately not consulted here — nor is an ancestor
     * directory's: these are the authenticated, fileId-only routes, which never see the link
     * token, so there is no way to tell "signed-in stranger who has the link" from "signed-in
     * stranger who doesn't" — granting on {@code callerId != null} alone would hand every
     * signed-in user permanent access to anything ever put in LINK mode. Anonymous/token-holding
     * access to a LINK file (or a descendant of a LINK folder) goes through the public routes
     * instead, which do check the token. */
    private Role resolveRole(File file, UUID callerId) {
        if (callerId == null) {
            return null;
        }
        Role best = grantedRole(file.getId(), callerId);
        for (File ancestor : ancestorDirectories(file)) {
            best = moreGenerous(best, grantedRole(ancestor.getId(), callerId));
        }
        return best;
    }

    /** The specific share row that explains why {@code callerId} can read {@code file} — their
     * own grant on it, or failing that, the nearest ancestor directory's. Unlike
     * {@link #effectiveRole} (which only needs the most generous role, for a permission check),
     * a listing that shows "공유한 사용자"/"공유된 날짜" for a shared directory's contents needs the
     * actual origin grant, so every child in the listing attributes to the same one. Empty when
     * the caller owns the file or holds no grant on it or any ancestor. */
    Optional<FileShare> resolveGrant(File file, UUID callerId) {
        if (callerId == null) {
            return Optional.empty();
        }
        Optional<FileShare> own = findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(file.getId()), callerId);
        if (own.isPresent()) {
            return own;
        }
        // ancestorDirectories() returns root-most first; walk it backwards so the first hit is
        // the nearest ancestor, matching ListFileSharesService's own nearest-wins tie-break.
        List<File> ancestors = ancestorDirectories(file);
        for (int i = ancestors.size() - 1; i >= 0; i--) {
            Optional<FileShare> grant =
                    findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(ancestors.get(i).getId()), callerId);
            if (grant.isPresent()) {
                return grant;
            }
        }
        return Optional.empty();
    }

    private Role grantedRole(UUID fileId, UUID callerId) {
        return findFileSharePort
                .findByFileIdAndSharedWithUserId(new FileId(fileId), callerId)
                .map(FileShare::getRole)
                .orElse(null);
    }

    /** The directories on {@code file}'s materialized path, root-most first. {@code file.path} is
     * the parent path, so each segment names one ancestor directory.
     * ponytail: one lookup per path segment (depth-bounded, typically &lt;5) — add an
     * effective-ACL cache only if deep trees or read volume make it hurt. */
    List<File> ancestorDirectories(File file) {
        List<File> ancestors = new ArrayList<>();
        String parentPath = file.getPath();
        if (parentPath == null || "/".equals(parentPath)) {
            return ancestors;
        }
        NamespaceId namespaceId = new NamespaceId(file.getNamespaceId());
        String walked = "/";
        for (String name : parentPath.substring(1).split("/")) {
            findFilePort.findActiveByNamespaceIdAndPathAndName(namespaceId, walked, name)
                    .filter(File::isDirectory)
                    .ifPresent(ancestors::add);
            walked = "/".equals(walked) ? "/" + name : walked + "/" + name;
        }
        return ancestors;
    }

    /** {@link Role} is a closed enum, not a permission set, so "combine two grants" is just
     * "take the one that grants more" — EDITOR ⊃ VIEWER. */
    private Role moreGenerous(Role a, Role b) {
        if (a == Role.EDITOR || b == Role.EDITOR) {
            return Role.EDITOR;
        }
        if (a == Role.VIEWER || b == Role.VIEWER) {
            return Role.VIEWER;
        }
        return null;
    }

    private boolean isOwner(File file, UUID callerId) {
        return callerId != null && callerId.equals(file.getOwnerId());
    }
}
