package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a {@code (fileId, key)} pair into a file for the anonymous routes. Access comes from:
 * <ul>
 *   <li>{@code fileId} alone, when the entry or an ancestor folder is LINK-scoped (see
 *       {@link FileAccessGuard#linkRoleFallback});</li>
 *   <li>otherwise {@code key} matching a guest share's invite {@code token} (see
 *       {@link FileShare#createPending}) minted for the entry or a folder above it.</li>
 * </ul>
 * Every rejection is the same FILE_NOT_FOUND, so an anonymous caller can't tell which check
 * failed. Shared by every public route so metadata and download agree on which links are live.
 */
@Component
@RequiredArgsConstructor
class PublicFileResolver {

    private final FindFilePort findFilePort;
    private final FindFileSharePort findFileSharePort;
    private final FileAccessGuard fileAccessGuard;

    /** The entry at {@code fileId}, if the link or the invite reaches it. */
    File resolve(String fileId, String key) {
        File target = target(fileId);
        List<File> ancestors = fileAccessGuard.ancestorDirectories(target);
        if (fileAccessGuard.linkRoleFallback(target, ancestors) != null || matchesGuestInvite(target, key, ancestors)) {
            return target;
        }
        throw notFound();
    }

    /** Live direct children of the directory at {@code fileId}, if the link or the invite
     * reaches it. */
    List<File> resolveChildren(String fileId, String key) {
        File dir = target(fileId);
        if (!dir.isDirectory()) {
            throw notFound();
        }
        List<File> ancestors = fileAccessGuard.ancestorDirectories(dir);
        if (fileAccessGuard.linkRoleFallback(dir, ancestors) == null && !matchesGuestInvite(dir, key, ancestors)) {
            throw notFound();
        }
        return findFilePort
                .findByNamespaceIdAndPath(new NamespaceId(dir.getNamespaceId()), dir.fullPath())
                .stream()
                .filter(f -> !f.isRemoved())
                .toList();
    }

    private File target(String fileId) {
        return parseUuid(fileId)
                .flatMap(id -> findFilePort.findById(new FileId(id)))
                .filter(file -> !file.isRemoved())
                .orElseThrow(this::notFound);
    }

    /** True when {@code key} is a guest invite minted for this entry or a directory above it. */
    private boolean matchesGuestInvite(File target, String key, List<File> ancestors) {
        return parseUuid(key)
                .flatMap(findFileSharePort::findByToken)
                .filter(share -> mintedForTargetOrAnAncestor(share, target, ancestors))
                .isPresent();
    }

    private boolean mintedForTargetOrAnAncestor(FileShare share, File target, List<File> ancestors) {
        if (share.getFileId().equals(target.getId())) {
            return true;
        }
        return ancestors.stream().anyMatch(ancestor -> share.getFileId().equals(ancestor.getId()));
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

    private BusinessException notFound() {
        return new BusinessException(FileExceptionCase.FILE_NOT_FOUND);
    }
}
