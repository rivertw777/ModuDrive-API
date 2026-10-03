package com.moduDrive.storage.adapter.in.messaging;

import com.moduDrive.common.core.annotation.EventListener;
import com.moduDrive.common.event.file.BlocksPurgeRequested;
import com.moduDrive.common.event.file.FileQueues;
import com.moduDrive.storage.application.port.in.command.PurgeStoredFileCommand;
import com.moduDrive.storage.application.port.in.command.PurgeStoredFileCommand.StoredVersion;
import com.moduDrive.storage.application.port.in.usecase.PurgeStoredFileUseCase;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;

@EventListener
@RequiredArgsConstructor
class FileEventListener {

    private final PurgeStoredFileUseCase purgeStoredFileUseCase;

    /** No idempotency check: deleting blocks that are already gone is a no-op, so a redelivery just
     * repeats it harmlessly. A failed delete throws and comes back for a retry. */
    @SqsListener(FileQueues.BLOCKS_PURGE_REQUESTED)
    void onBlocksPurgeRequested(BlocksPurgeRequested event) {
        purgeStoredFileUseCase.purgeStoredFile(new PurgeStoredFileCommand(event.fileId(), event.versions().stream()
                .map(v -> new StoredVersion(v.s3Path(), v.blockCount()))
                .toList()));
    }
}
