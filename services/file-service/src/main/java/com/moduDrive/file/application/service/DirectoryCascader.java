package com.moduDrive.file.application.service;

import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.ReleaseBlocksPort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Cascades an operation on a directory (move, rename, trash, restore, purge) onto every row under
 * it. {@code path} is materialized, so every descendant's path starts with the directory's full
 * path.
 */
@Component
@RequiredArgsConstructor
class DirectoryCascader {

    private final FindFilePort findFilePort;
    private final SaveFilePort saveFilePort;
    private final ReleaseBlocksPort releaseBlocksPort;

    // Each cascade is a few set-based statements, not a load-and-save per descendant: the subtree is
    // locked top-down in one query (like a commit locks the folders above its file, so the two never
    // wait on each other in a cycle), then rewritten in one UPDATE.

    /** Rewrites the path prefix of every descendant after the directory itself moved/was renamed. */
    void movePath(NamespaceId namespaceId, String oldPrefix, String newPrefix) {
        if (oldPrefix.equals(newPrefix)) return;
        saveFilePort.moveSubtree(namespaceId, oldPrefix, newPrefix);
    }

    /** {@code trashedAt} is the directory's own: the whole cascade shares one instant (see
     * {@link #purge}). The subtree is locked first, so a commit under it finishes before. A
     * descendant already trashed or purged keeps its own trashedAt. */
    void softDelete(NamespaceId namespaceId, String directoryFullPath, LocalDateTime trashedAt) {
        saveFilePort.trashSubtree(namespaceId, directoryFullPath, trashedAt);
    }

    /** Restores every still-trashed descendant; a purged one is a tombstone whose content is
     * gone. A file trashed on its own before its folder was trashed comes back too. */
    void restore(NamespaceId namespaceId, String directoryFullPath) {
        saveFilePort.restoreSubtree(namespaceId, directoryFullPath);
    }

    /** Tombstones every still-TRASHED descendant and releases its blocks.
     *
     * <p>{@code rootTrashedAt}: a trashed directory frees its name, so a second directory can be
     * created and trashed at the same path while the first is still in retention, and a path
     * prefix alone can't tell their descendants apart. The root's own cascade shares its
     * {@code trashedAt}; a descendant trashed later belongs to the other directory and is left
     * alone. {@code deletedBy} is null for the retention sweep. */
    void purge(NamespaceId namespaceId, String directoryFullPath, LocalDateTime rootTrashedAt, UUID deletedBy) {
        // Locked like softDelete/restore, so a restore running now finishes first and is seen.
        List<FileId> purged = findFilePort.lockTrashedByNamespaceIdAndPathStartingWith(namespaceId, directoryFullPath).stream()
                .filter(descendant -> descendant.getTrashedAt() == null || rootTrashedAt == null
                        || !descendant.getTrashedAt().isAfter(rootTrashedAt))
                .map(descendant -> new FileId(descendant.getId()))
                .toList();
        // The whole subtree's blocks in one release, before purgeFiles deletes the versions pointing
        // at them (see FilePurger), in the hash order a commit locks them in. A nested subdirectory
        // has no versions, so it adds nothing.
        releaseBlocksPort.releaseBlocksOfFiles(purged.stream().map(FileId::value).toList());
        saveFilePort.purgeFiles(purged, deletedBy);
    }
}
