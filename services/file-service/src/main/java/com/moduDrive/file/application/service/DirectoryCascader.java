package com.moduDrive.file.application.service;

import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.PurgeStorageBlocksPort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.File.FilePath;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

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
    private final PurgeStorageBlocksPort purgeStorageBlocksPort;

    /** Rewrites the path prefix of every descendant after the directory itself moved/was renamed. */
    void movePath(NamespaceId namespaceId, String oldPrefix, String newPrefix) {
        if (oldPrefix.equals(newPrefix)) return;

        forEachDescendant(namespaceId, oldPrefix, descendant -> {
            String rest = descendant.getPath().substring(oldPrefix.length());
            descendant.move(new FilePath(newPrefix + rest));
            saveFilePort.saveFile(descendant);
        });
    }

    /** Soft-deletes every descendant along with the directory being sent to trash. */
    void softDelete(NamespaceId namespaceId, String directoryFullPath) {
        forEachDescendant(namespaceId, directoryFullPath, descendant -> {
            if (descendant.getStatus() == FileStatus.DELETED) return;
            descendant.softDelete();
            saveFilePort.saveFile(descendant);
        });
    }

    /** Restores every descendant along with the directory being restored from trash.
     * ponytail: restores the whole subtree unconditionally, so a file trashed individually
     * before its parent folder was trashed comes back too — track trash provenance separately
     * if that distinction ever matters. */
    void restore(NamespaceId namespaceId, String directoryFullPath) {
        forEachDescendant(namespaceId, directoryFullPath, descendant -> {
            if (descendant.getStatus() != FileStatus.DELETED) return;
            descendant.restore();
            saveFilePort.saveFile(descendant);
        });
    }

    /** Permanently deletes every descendant along with the directory being purged from trash.
     * Skips a descendant that isn't DELETED — e.g. restored individually before the parent
     * directory was purged — so purge can't destroy a file the user already pulled out of trash.
     *
     * {@code rootUpdatedAt}: a trashed directory's {@code active_slot_name} goes NULL (see
     * {@code FileJpaEntity}), so its name/path is immediately reusable — a second, unrelated
     * directory can be created and later trashed at that exact same path while the first is
     * still in retention. Both share one {@code fullPath()}, so a path-prefix lookup alone can't
     * tell their descendants apart; purging the older one would otherwise also destroy the
     * newer, still-in-retention one's contents. Descendants of the same cascade the root belongs
     * to were soft-deleted in the same call as the root (see {@link #softDelete}), so they share
     * its {@code updatedAt} — a descendant trashed strictly later belongs to a different,
     * unrelated directory instance and must be left alone. */
    void purge(NamespaceId namespaceId, String directoryFullPath, LocalDateTime rootUpdatedAt) {
        forEachDescendant(namespaceId, directoryFullPath, descendant -> {
            if (descendant.getStatus() != FileStatus.DELETED) return;
            if (descendant.getUpdatedAt().isAfter(rootUpdatedAt)) return;
            // A nested subdirectory has no blocks of its own — only a real file does.
            if (!descendant.isDirectory()) {
                FileId fileId = new FileId(descendant.getId());
                UUID ownerId = descendant.getOwnerId();
                // Deferred to after commit — see FilePurger's javadoc; the block delete can't be
                // rolled back, so it must not run before the row delete below is durable.
                AfterCommit.run(() -> purgeStorageBlocksPort.purgeBlocks(fileId, ownerId));
            }
            saveFilePort.deleteFile(new FileId(descendant.getId()));
        });
    }

    private void forEachDescendant(NamespaceId namespaceId, String prefix, Consumer<File> action) {
        List<File> descendants = findFilePort.findByNamespaceIdAndPathStartingWith(namespaceId, prefix);
        descendants.forEach(action);
    }
}
