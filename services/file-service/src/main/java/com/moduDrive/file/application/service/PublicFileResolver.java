package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.domain.model.ShareScope;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a token into a file for the unauthenticated routes. Two independent token spaces share
 * this one path: a file's own {@code linkToken} ("anyone with the link", scope LINK) and a
 * pending guest share's per-invite {@code token} (one specific invited email, scope stays
 * RESTRICTED — see {@link com.moduDrive.file.domain.model.FileShare#createPending}). Every
 * rejection is the same FILE_NOT_FOUND regardless of which space almost matched: an anonymous
 * caller must not be able to tell "malformed token" from "wrong token" from "right token, sharing
 * switched off" from "right token, file trashed" from "right token, invite revoked".
 * <p>
 * When the link token belongs to a <b>directory</b>, everything nested under it is reachable
 * through the same token (Google-Drive-style folder link): {@link #resolveChildren} lists a
 * level, {@link #resolveDescendant} resolves one entry, and both check the entry really is under
 * that folder so one folder's token can never reach another's contents.
 * <p>
 * Shared by every public route so the metadata and the download paths can never disagree about
 * which tokens are live.
 */
@Component
@RequiredArgsConstructor
class PublicFileResolver {

    private final FindFilePort findFilePort;
    private final FindFileSharePort findFileSharePort;

    File resolve(String token) {
        return parseUuid(token)
                .flatMap(this::resolveByToken)
                .filter(file -> file.getStatus() != FileStatus.DELETED)
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
    }

    /** Children of the folder {@code token} link-shares, or of {@code parentId} when that is a
     * directory nested under it. {@code parentId} may be null / blank (the folder itself) or the
     * folder's own id. */
    List<File> resolveChildren(String token, String parentId) {
        File folder = requireLinkFolder(token);
        File parent = folder;
        Optional<UUID> requestedParent = parseUuid(parentId);
        if (requestedParent.isPresent() && !requestedParent.get().equals(folder.getId())) {
            parent = findFilePort.findById(new FileId(requestedParent.get()))
                    .filter(f -> f.getStatus() != FileStatus.DELETED)
                    .filter(File::isDirectory)
                    .filter(f -> isWithin(folder, f))
                    .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        } else if (parentId != null && !parentId.isBlank() && requestedParent.isEmpty()) {
            // A non-blank parentId that isn't even a UUID is a wrong value, not "list the root".
            throw new BusinessException(FileExceptionCase.FILE_NOT_FOUND);
        }
        return findFilePort
                .findByNamespaceIdAndPath(new NamespaceId(parent.getNamespaceId()), parent.fullPath())
                .stream()
                .filter(f -> f.getStatus() != FileStatus.DELETED)
                .toList();
    }

    /** One file or directory nested under the folder {@code token} link-shares. */
    File resolveDescendant(String token, String descendantId) {
        File folder = requireLinkFolder(token);
        UUID id = parseUuid(descendantId)
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        return findFilePort.findById(new FileId(id))
                .filter(f -> f.getStatus() != FileStatus.DELETED)
                .filter(f -> isWithin(folder, f))
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
    }

    private File requireLinkFolder(String token) {
        return parseUuid(token)
                .flatMap(findFilePort::findByLinkToken)
                .filter(f -> f.getStatus() != FileStatus.DELETED)
                .filter(f -> f.getAccessScope() == ShareScope.LINK)
                .filter(File::isDirectory)
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
    }

    /** {@code folder.fullPath()} is the string prefix every descendant stores as (or under) its
     * own {@code path} — see {@code DirectoryCascader}. */
    private boolean isWithin(File folder, File candidate) {
        if (!folder.getNamespaceId().equals(candidate.getNamespaceId())) {
            return false;
        }
        String base = folder.fullPath();
        return candidate.getPath().equals(base) || candidate.getPath().startsWith(base + "/");
    }

    private Optional<File> resolveByToken(UUID token) {
        Optional<File> linkShared = findFilePort.findByLinkToken(token)
                .filter(file -> file.getAccessScope() == ShareScope.LINK);
        if (linkShared.isPresent()) {
            return linkShared;
        }
        // A pending guest share's token stays live independently of the file's own accessScope —
        // it is not affected by the file ever having been, or never being, LINK-shared.
        return findFileSharePort.findByToken(token)
                .flatMap(share -> findFilePort.findById(new FileId(share.getFileId())));
    }

    private Optional<UUID> parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
