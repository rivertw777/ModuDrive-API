package com.moduDrive.file.adapter.in.scheduler;

import com.moduDrive.file.application.port.in.usecase.PurgeUnreferencedBlocksUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Clock-triggered inbound adapter. No ShedLock: each batch claims its rows with
 * {@code FOR UPDATE SKIP LOCKED}, so instances running it at once split the work. One transaction
 * per batch, so a failure only loses that batch. */
@Component
@RequiredArgsConstructor
class UnreferencedBlockScheduler {

    private final PurgeUnreferencedBlocksUseCase purgeUnreferencedBlocksUseCase;

    @Scheduled(fixedDelay = 60 * 60 * 1000, initialDelay = 60 * 1000) // hourly
    public void purgeUnreferencedBlocks() {
        int batchSize;
        do {
            batchSize = purgeUnreferencedBlocksUseCase.purgeUnreferencedBlocks();
        } while (batchSize >= PurgeUnreferencedBlocksUseCase.BATCH_SIZE);
    }
}
