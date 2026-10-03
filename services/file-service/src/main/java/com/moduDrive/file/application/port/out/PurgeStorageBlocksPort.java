package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileVersion;

import java.util.List;

public interface PurgeStorageBlocksPort {

    /** Asks storage-service to delete every block of these versions. Call it inside the transaction
     * that tombstones the file: the request is recorded with it (outbox) and goes out only once it
     * commits, so blocks are never deleted for a purge that rolled back. {@code versions} are read
     * before that same transaction deletes their rows. */
    void purgeBlocks(FileId fileId, List<FileVersion> versions);
}
