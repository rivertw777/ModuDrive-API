package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.file.application.port.in.usecase.PurgeUnreferencedBlocksUseCase;
import com.moduDrive.file.application.port.out.ClaimUnreferencedBlocksPort;
import com.moduDrive.file.application.port.out.ClaimUnreferencedBlocksPort.UnreferencedBlock;
import com.moduDrive.file.application.port.out.PurgeStorageBlocksPort;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Spec 001: a block whose last reference went away is kept for a day — committing the same bytes
 * again in that time (re-uploading a file right after emptying the trash) just references it again.
 * After that its row is deleted and storage-service told to drop the object, in one transaction
 * (outbox). */
@UseCase
@RequiredArgsConstructor
class PurgeUnreferencedBlocksService implements PurgeUnreferencedBlocksUseCase {

    static final Duration GRACE = Duration.ofHours(24);

    private final ClaimUnreferencedBlocksPort claimUnreferencedBlocksPort;
    private final PurgeStorageBlocksPort purgeStorageBlocksPort;

    @Transactional
    @Override
    public int purgeUnreferencedBlocks() {
        Instant decidedAt = Instant.now();
        List<UnreferencedBlock> claimed = claimUnreferencedBlocksPort.claimUnreferenced(
                LocalDateTime.now().minus(GRACE), BATCH_SIZE);
        Map<UUID, List<String>> hashesByOwner = claimed.stream().collect(Collectors.groupingBy(
                UnreferencedBlock::ownerId, Collectors.mapping(UnreferencedBlock::hash, Collectors.toList())));
        hashesByOwner.forEach((ownerId, hashes) -> purgeStorageBlocksPort.purgeBlocks(ownerId, hashes, decidedAt));
        return claimed.size();
    }
}
