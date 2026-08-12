package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.FileAccess;

public interface SaveFileAccessPort {

    /** Upserts by (userId, fileId) — a re-open just bumps {@code accessedAt} on the
     * existing row instead of growing one row per view. */
    void recordAccess(FileAccess fileAccess);
}
