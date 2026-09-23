package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.FileAccess;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SaveFileAccessPort {

    /** Upserts by (userId, fileId) — a re-open just bumps {@code accessedAt} on the
     * existing row instead of growing one row per view. */
    void recordAccess(FileAccess fileAccess);

    /** {@link #recordAccess} for many files of one user in a single pass, joining the caller's
     * transaction instead of flushing per file. */
    void recordAccesses(UUID userId, List<UUID> fileIds, LocalDateTime accessedAt);
}
