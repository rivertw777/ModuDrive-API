package com.moduDrive.common.event.file;

import java.util.List;
import java.util.UUID;

/** Published by file-service (queue {@link FileQueues#BLOCKS_PURGE_REQUESTED}) in the transaction that
 * tombstones a purged file — the same one that deletes its version rows, so the block locations travel
 * in the event: once it commits, file-service no longer knows them. storage-service deletes every
 * version's blocks. Deleting blocks that are already gone is a no-op, so a redelivery needs no
 * idempotency check. */
public record BlocksPurgeRequested(UUID fileId, List<StoredVersion> versions) {

    /** Where one version's blocks live: {@code blockCount} blocks under {@code s3Path}. */
    public record StoredVersion(String s3Path, int blockCount) {
    }
}
