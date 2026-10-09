package com.moduDrive.file.adapter.out.messaging;

import com.moduDrive.common.core.annotation.EventPublisher;
import com.moduDrive.common.event.file.BlocksPurgeRequested;
import com.moduDrive.common.event.file.FileQueues;
import com.moduDrive.common.infrastructure.messaging.outbox.OutboxEventRecorder;
import com.moduDrive.file.application.port.out.PurgeStorageBlocksPort;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@EventPublisher
@RequiredArgsConstructor
class OutboxStorageEventPublisher implements PurgeStorageBlocksPort {

    private final OutboxEventRecorder outboxEventRecorder;

    @Override
    public void purgeBlocks(UUID ownerId, List<String> hashes, Instant decidedAt) {
        if (hashes.isEmpty()) {
            return;
        }
        outboxEventRecorder.record(FileQueues.BLOCKS_PURGE_REQUESTED, ownerId.toString(),
                new BlocksPurgeRequested(ownerId, List.copyOf(hashes), decidedAt));
    }
}
