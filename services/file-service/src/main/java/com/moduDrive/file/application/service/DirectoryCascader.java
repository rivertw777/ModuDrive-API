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
 * `path` is a materialized path (a directory's own location is its parent's {@code path} +
 * {@code name}), so every descendant stores that directory's full path as a string prefix.
 * An operation on a directory (move, rename, trash, restore, purge) only ever touches that one
 * row — this cascades the same operation onto every row nested under it so the subtree doesn't
 * get orphaned from (or left behind by) its directory.
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

    /** Soft-deletes every descendant along with the directory being sent to trash. {@code trashedAt}
     * is the directory's own — the whole cascade shares one instant (see {@link #purge}). Locked,
     * like the folder itself (DeleteFileService): a commit adding a version or a file under it
     * finishes first, and its rows are trashed as they are after it. A descendant already trashed
     * (individually, earlier) or already purged keeps its trashedAt — see purge()'s javadoc for why
     * that matters. */
    void softDelete(NamespaceId namespaceId, String directoryFullPath, LocalDateTime trashedAt) {
        saveFilePort.trashSubtree(namespaceId, directoryFullPath, trashedAt);
    }

    /** Restores every descendant along with the directory being restored from trash. Only a
     * still-trashed descendant is restorable — one purged individually before the parent folder is
     * restored is a tombstone (status DELETED): its content is gone, restoring the row would
     * resurrect an empty file.
     * ponytail: restores the whole subtree unconditionally, so a file trashed individually
     * before its parent folder was trashed comes back too — track trash provenance separately
     * if that distinction ever matters. */
    void restore(NamespaceId namespaceId, String directoryFullPath) {
        saveFilePort.restoreSubtree(namespaceId, directoryFullPath);
    }

    /** Purges every descendant along with the directory being purged from trash (tombstones the
     * rows, drops their blocks). Skips a descendant that isn't currently TRASHED — e.g. restored
     * individually before the parent directory was purged, or already purged (status DELETED) —
     * so a re-run doesn't re-delete blocks.
     *
     * {@code rootTrashedAt}: a trashed directory's {@code active_slot_name} goes NULL (see
     * {@code FileJpaEntity}), so its name/path is immediately reusable — a second, unrelated
     * directory can be created and later trashed at that exact same path while the first is
     * still in retention. Both share one {@code fullPath()}, so a path-prefix lookup alone can't
     * tell their descendants apart; purging the older one would otherwise also destroy the
     * newer, still-in-retention one's contents. Descendants of the same cascade the root belongs
     * to were soft-deleted in the same call as the root (see {@link #softDelete}), so they share
     * its {@code trashedAt} — a descendant trashed strictly later belongs to a different,
     * unrelated directory instance and must be left alone.
     *
     * {@code deletedBy} is null for a system-triggered purge (the retention sweep) — every
     * descendant's tombstone shares the same value as the root, same as {@code rootTrashedAt}. */
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
