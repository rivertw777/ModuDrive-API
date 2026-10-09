package com.moduDrive.storage.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ClaimStaleUploadsPort {

    /** Removes from the sweep schedule and returns up to {@code limit} blocks last uploaded before
     * {@code cutoff}. Concurrent claims never return the same block; uploading a block again moves
     * it back out of reach. */
    List<UploadedBlock> claimStale(Instant cutoff, int limit);

    record UploadedBlock(UUID ownerId, String hash) {}
}
