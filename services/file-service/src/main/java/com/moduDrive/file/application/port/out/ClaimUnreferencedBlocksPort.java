package com.moduDrive.file.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ClaimUnreferencedBlocksPort {

    /** Deletes up to {@code limit} block rows that nothing has referenced since before
     * {@code cutoff}, and returns them. Concurrent claims never return the same row, and a row a
     * commit is holding is skipped. */
    List<UnreferencedBlock> claimUnreferenced(LocalDateTime cutoff, int limit);

    record UnreferencedBlock(UUID ownerId, String hash) {}
}
