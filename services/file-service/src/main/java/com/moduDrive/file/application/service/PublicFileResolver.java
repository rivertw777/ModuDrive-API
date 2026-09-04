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
 * Turns a {@code (fileId, key)} pair into a file for the unauthenticated routes — the
 * Google-Drive-style stable link {@code /public/{fileId}?key={token}}. {@code fileId} identifies
 * the entry; {@code key} is the capability that authorizes it and comes from one of two
 * independent spaces:
 * <ul>
 *   <li>a file's or folder's own {@code linkToken} ("anyone with the link", scope LINK) — opens
 *       that entry <b>and everything nested under it</b>;</li>
 *   <li>a pending/claimed guest share's per-invite {@code token} (see
 *       {@link com.moduDrive.file.domain.model.FileShare#createPending}) — opens <b>only the one
 *       entry it was minted for</b>, never a subtree or a directory listing.</li>
 * </ul>
 * The requested {@code fileId} is allowed only if it is the entry the key unlocks or (for a link
 * token) nested under it, so one folder's key can never reach another's contents. Every rejection
 * is the same FILE_NOT_FOUND regardless of which check almost passed: an anonymous caller must
 * not be able to tell "malformed" from "wrong key" from "right key, sharing switched off" from
 * "right key, file trashed" from "right key, wrong fileId".
 * <p>
 * Shared by every public route so the metadata and the download paths can never disagree about
 * which links are live.
 */
@Component
@RequiredArgsConstructor
class PublicFileResolver {

    private final FindFilePort findFilePort;
    private final FindFileSharePort findFileSharePort;

    /** The entry at {@code fileId}, provided {@code key} unlocks it (or, for a link token, an
     * ancestor folder). */
    File resolve(String fileId, String key) {
        Unlocked unlocked = unlockRoot(key);
        File target = target(fileId);
        if (!unlocks(unlocked, target)) {
            throw notFound();
        }
        return target;
    }

    /** Direct children of the directory at {@code fileId} (a link-shared folder, or one nested
     * under it), DELETED entries excluded. A per-invite guest token cannot reach this — listing a
     * folder needs the folder itself to be "anyone with the link". */
    List<File> resolveChildren(String fileId, String key) {
        Unlocked unlocked = unlockRoot(key);
        if (!unlocked.subtree()) {
            throw notFound();
        }
        File dir = target(fileId);
        if (!unlocks(unlocked, dir) || !dir.isDirectory()) {
            throw notFound();
        }
        return findFilePort
                .findByNamespaceIdAndPath(new NamespaceId(dir.getNamespaceId()), dir.fullPath())
                .stream()
                .filter(f -> f.getStatus() != FileStatus.DELETED)
                .toList();
    }

    private File target(String fileId) {
        return parseUuid(fileId)
                .flatMap(id -> findFilePort.findById(new FileId(id)))
                .filter(file -> file.getStatus() != FileStatus.DELETED)
                .orElseThrow(this::notFound);
    }

    /** What {@code key} was minted for: a live LINK-scoped {@code linkToken} (subtree reachable),
     * or a guest share's {@code token} — valid independently of the file's own scope, but scoped
     * to that one entry. */
    private Unlocked unlockRoot(String key) {
        UUID capability = parseUuid(key).orElseThrow(this::notFound);
        Optional<File> linkShared = findFilePort.findByLinkToken(capability)
                .filter(f -> f.getStatus() != FileStatus.DELETED)
                .filter(f -> f.getAccessScope() == ShareScope.LINK);
        if (linkShared.isPresent()) {
            return new Unlocked(linkShared.get(), true);
        }
        File guestRoot = findFileSharePort.findByToken(capability)
                .flatMap(share -> findFilePort.findById(new FileId(share.getFileId())))
                .filter(f -> f.getStatus() != FileStatus.DELETED)
                .orElseThrow(this::notFound);
        return new Unlocked(guestRoot, false);
    }

    /** True when {@code target} is the unlocked entry itself, or — for a link token on a
     * directory — an entry nested under it. {@code root.fullPath()} is the string prefix every
     * descendant stores as (or under) its own {@code path}; see {@code DirectoryCascader}. */
    private boolean unlocks(Unlocked unlocked, File target) {
        File root = unlocked.root();
        if (root.getId().equals(target.getId())) {
            return true;
        }
        if (!unlocked.subtree() || !root.isDirectory()
                || !root.getNamespaceId().equals(target.getNamespaceId())) {
            return false;
        }
        String base = root.fullPath();
        return target.getPath().equals(base) || target.getPath().startsWith(base + "/");
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

    private record Unlocked(File root, boolean subtree) {}
}
