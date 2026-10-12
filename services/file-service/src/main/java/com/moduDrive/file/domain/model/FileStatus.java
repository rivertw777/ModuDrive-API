package com.moduDrive.file.domain.model;

import java.util.Set;

public enum FileStatus {
    /** Created, no committed version yet. */
    PENDING,
    UPLOADED,
    /** In the trash, restorable. {@code File.trashedAt} is set. */
    TRASHED,
    /** Purged from the trash: a tombstone row, reached only from TRASHED. {@code File.deletedAt}
     * is set. */
    DELETED;

    /** No longer part of the live file tree. */
    public static final Set<FileStatus> REMOVED = Set.of(TRASHED, DELETED);
}
