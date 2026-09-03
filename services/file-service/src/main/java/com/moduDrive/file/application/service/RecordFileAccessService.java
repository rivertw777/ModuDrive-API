package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.file.application.port.in.command.RecordFileAccessCommand;
import com.moduDrive.file.application.port.in.usecase.RecordFileAccessUseCase;
import com.moduDrive.file.application.port.out.SaveFileAccessPort;
import com.moduDrive.file.domain.model.FileAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Slf4j
@UseCase
@RequiredArgsConstructor
class RecordFileAccessService implements RecordFileAccessUseCase {

    private final SaveFileAccessPort saveFileAccessPort;

    // Recency tracking is a side effect of "opening" a file — inline preview/view, upload, and
    // fetching its detail metadata (Google Drive-style: a plain download or a metadata edit like
    // rename/move/share does NOT touch "recent"). It is never the reason that action fails — a
    // lost upsert race or any other runtime error here must not turn an otherwise-successful
    // call into a 500. Losing an individual access record just means one action doesn't move
    // that file to the top of "recent"; harmless for this feature. Catching RuntimeException
    // broadly (not just DataAccessException) is what lets every caller add this as a single line
    // with no try/catch of its own — see GetFileController, UploadFileMetadataController and
    // GetLatestFileVersionsController (the storage-service preview path).
    //
    // Deliberately NOT @Transactional: the adapter's saveAndFlush already runs in its own
    // Spring Data-managed transaction, and wrapping this method in another one means a
    // constraint violation marks *that* transaction rollback-only — the catch below swallows
    // the exception, but the @Transactional proxy then throws UnexpectedRollbackException at
    // commit time, from outside this try block. That defeats the whole point of catching here.
    @Override
    public void recordAccess(RecordFileAccessCommand command) {
        try {
            saveFileAccessPort.recordAccess(
                    FileAccess.of(command.getUserId(), command.getFileId(), LocalDateTime.now()));
        } catch (RuntimeException e) {
            log.warn("Failed to record file access for user={} file={}",
                    command.getUserId().value(), command.getFileId().value(), e);
        }
    }
}
