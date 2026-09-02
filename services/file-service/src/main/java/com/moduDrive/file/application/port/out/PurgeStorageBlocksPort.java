package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.File.FileId;

import java.util.UUID;

public interface PurgeStorageBlocksPort {

    /** Permanently deletes every stored block for every version of this file. {@code ownerId} is
     * always the file's own owner — the caller already verified ownership before reaching this
     * point, and storage-service's revision lookup re-checks it against file-service. */
    void purgeBlocks(FileId fileId, UUID ownerId);
}
