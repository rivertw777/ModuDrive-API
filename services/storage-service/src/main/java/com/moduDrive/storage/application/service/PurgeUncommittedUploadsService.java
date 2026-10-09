package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.storage.application.port.in.usecase.PurgeUncommittedUploadsUseCase;
import com.moduDrive.storage.application.port.out.ClaimStaleUploadsPort;
import com.moduDrive.storage.application.port.out.ClaimStaleUploadsPort.UploadedBlock;
import com.moduDrive.storage.application.port.out.DeleteBlocksPort;
import com.moduDrive.storage.application.port.out.FindCommittedBlocksPort;
import com.moduDrive.storage.domain.model.Blocks;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Deletes blocks that were uploaded and never committed. Only uploads older than
 * {@link Blocks#UPLOAD_TTL} (plus a margin) are claimed — by then their upload record is gone, so no
 * commit can still use them. A block that did get committed is file-service's to delete, so it is
 * left alone. */
@UseCase
@RequiredArgsConstructor
class PurgeUncommittedUploadsService implements PurgeUncommittedUploadsUseCase {

    private static final Logger logger = LoggerFactory.getLogger(PurgeUncommittedUploadsService.class);
    private static final int BATCH_SIZE = 100;
    private static final Duration MARGIN = Duration.ofHours(1);

    private final ClaimStaleUploadsPort claimStaleUploadsPort;
    private final FindCommittedBlocksPort findCommittedBlocksPort;
    private final DeleteBlocksPort deleteBlocksPort;

    @Override
    public void purgeUncommittedUploads() {
        Instant decidedAt = Instant.now();
        Instant cutoff = decidedAt.minus(Blocks.UPLOAD_TTL).minus(MARGIN);
        List<UploadedBlock> batch;
        do {
            batch = claimStaleUploadsPort.claimStale(cutoff, BATCH_SIZE);
            Map<UUID, List<String>> hashesByOwner = batch.stream().collect(Collectors.groupingBy(
                    UploadedBlock::ownerId, Collectors.mapping(UploadedBlock::hash, Collectors.toList())));
            hashesByOwner.forEach((ownerId, hashes) -> purge(ownerId, hashes, decidedAt));
        } while (batch.size() == BATCH_SIZE);
    }

    private void purge(UUID ownerId, List<String> hashes, Instant decidedAt) {
        try {
            Set<String> committed = findCommittedBlocksPort.findCommitted(ownerId, hashes);
            hashes.stream()
                    .filter(hash -> !committed.contains(hash))
                    .forEach(hash -> deleteBlocksPort.deleteUnlessRewritten(Blocks.key(ownerId, hash), decidedAt));
        } catch (RuntimeException e) {
            // ponytail: already claimed, so a failure here is not retried — those blocks stay
            // orphaned (logged). Re-queue on failure if this shows up in the logs.
            logger.error("Failed to purge {} uncommitted block(s) of owner {}", hashes.size(), ownerId, e);
        }
    }
}
