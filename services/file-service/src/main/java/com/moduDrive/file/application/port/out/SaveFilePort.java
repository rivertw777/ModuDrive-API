package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SaveFilePort {

    File saveFile(File file);

    /** Inserts brand-new rows (null id) all at once — one flush instead of one per row, which a
     * 5,000-entry upload batch needs. Same slot-conflict translation as {@link #saveFile}; the
     * result is in input order. */
    List<File> saveNewFiles(List<File> files);

    /** Purge from trash: drop blocks/versions/shares/favorites, keep the metadata row as a
     * tombstone with {@code deletedAt} stamped. {@code deletedBy} is null for a system-triggered
     * purge (the retention sweep) — there's no caller to attribute it to. */
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

    /** Hard delete — everything, row included. Not used by the trash flow (see {@link #purgeFile});
     * for a future tombstone-cleanup job. */
    void deleteFile(FileId fileId);
}
