package com.moduDrive.file.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PurgeStorageBlocksPort {

    /** Asks storage-service to delete these blocks of the owner. Call it inside the transaction that
     * deletes their rows: the request is recorded with it (outbox) and goes out only once it
     * commits. storage-service leaves alone any block written after {@code decidedAt} — an upload
     * of the same bytes that started after the rows were gone. */
    void purgeBlocks(UUID ownerId, List<String> hashes, Instant decidedAt);
}
