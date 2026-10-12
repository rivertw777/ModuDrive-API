package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

public interface SaveFilePort {

    File saveFile(File file);

    /** Purge from trash: drop versions/shares/favorites, keep the row as a tombstone with
     * {@code deletedAt} stamped. {@code deletedBy} is null for the retention sweep. */
    void purgeFile(FileId fileId, UUID deletedBy);

    /** {@link #purgeFile} for many files at once — a folder's subtree. A constant number of
     * statements however many files. The caller holds the rows' locks (they're TRASHED and stay so). */
    void purgeFiles(Collection<FileId> fileIds, UUID deletedBy);

    /** Subtree rewrites for an operation on a directory: every row whose {@code path} is
     * {@code prefix} or nested under it. Each locks the subtree top-down first (the order a commit
     * locks the folders above its file), then rewrites it in one statement — the same change the
     * matching {@link File} method makes, applied without loading the rows.
     * Moves the subtree from under {@code prefix} to under {@code newPrefix}. */
    void moveSubtree(NamespaceId namespaceId, String prefix, String newPrefix);

    /** {@link File#softDelete} on every row not already trashed or purged. */
    void trashSubtree(NamespaceId namespaceId, String prefix, LocalDateTime trashedAt);

    /** {@link File#restore} on every still-trashed row. */
    void restoreSubtree(NamespaceId namespaceId, String prefix);
}
