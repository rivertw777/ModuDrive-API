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

    // Recording "recent" never fails the call that opened the file (detail view, preview): any
    // RuntimeException, e.g. a lost upsert race, is logged and dropped, so callers need no
    // try/catch.
    //
    // Not @Transactional: a constraint violation would mark the outer transaction rollback-only,
    // and its commit would then throw UnexpectedRollbackException outside this catch.
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
