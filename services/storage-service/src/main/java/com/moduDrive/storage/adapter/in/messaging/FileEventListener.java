package com.moduDrive.storage.adapter.in.messaging;

import com.moduDrive.common.core.annotation.EventListener;
import com.moduDrive.common.event.file.BlocksPurgeRequested;
import com.moduDrive.common.event.file.FileQueues;
import com.moduDrive.storage.application.port.in.command.PurgeBlocksCommand;
import com.moduDrive.storage.application.port.in.usecase.PurgeBlocksUseCase;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;

@EventListener
@RequiredArgsConstructor
class FileEventListener {

    private final PurgeBlocksUseCase purgeBlocksUseCase;

    /** No idempotency check: deleting blocks that are already gone is a no-op, so a redelivery just
     * repeats it harmlessly. A failed delete throws and comes back for a retry. */
    @SqsListener(FileQueues.BLOCKS_PURGE_REQUESTED)
    void onBlocksPurgeRequested(BlocksPurgeRequested event) {
        purgeBlocksUseCase.purgeBlocks(new PurgeBlocksCommand(event.ownerId(), event.hashes(), event.decidedAt()));
    }
}
