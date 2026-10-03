package com.moduDrive.file.adapter.out.messaging;

import com.moduDrive.common.core.annotation.EventPublisher;
import com.moduDrive.common.event.file.BlocksPurgeRequested;
import com.moduDrive.common.event.file.BlocksPurgeRequested.StoredVersion;
import com.moduDrive.common.event.file.FileQueues;
import com.moduDrive.common.infrastructure.messaging.outbox.OutboxEventRecorder;
import com.moduDrive.file.application.port.out.PurgeStorageBlocksPort;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileVersion;
import lombok.RequiredArgsConstructor;

import java.util.List;

@EventPublisher
@RequiredArgsConstructor
class OutboxStorageEventPublisher implements PurgeStorageBlocksPort {

    private final OutboxEventRecorder outboxEventRecorder;

    @Override
    public void purgeBlocks(FileId fileId, List<FileVersion> versions) {
        // A file that never got a version (an upload that didn't finish) has no blocks to drop.
        if (versions.isEmpty()) {
            return;
        }
        outboxEventRecorder.record(FileQueues.BLOCKS_PURGE_REQUESTED, fileId.value().toString(),
                new BlocksPurgeRequested(fileId.value(), versions.stream()
                        .map(v -> new StoredVersion(v.getS3Path(), v.getBlockCount()))
                        .toList()));
    }
}
