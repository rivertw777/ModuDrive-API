package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.file.application.port.in.command.RecordFileAccessCommand;
import com.moduDrive.file.application.port.in.usecase.RecordFileAccessUseCase;
import com.moduDrive.file.application.port.out.SaveFileAccessPort;
import com.moduDrive.file.domain.model.FileAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@UseCase
@RequiredArgsConstructor
class RecordFileAccessService implements RecordFileAccessUseCase {

    private final SaveFileAccessPort saveFileAccessPort;

    // Recency tracking is a side effect of viewing a file, never the reason a view fails —
    // a lost upsert race or a transient DB error here must not turn a successful GetFile
    // into a 500. Losing an individual access record just means one open doesn't move that
    // file to the top of "recent"; harmless for this feature.
    @Transactional
    @Override
    public void recordAccess(RecordFileAccessCommand command) {
        try {
            saveFileAccessPort.recordAccess(
                    FileAccess.of(command.getUserId(), command.getFileId(), LocalDateTime.now()));
        } catch (DataAccessException e) {
            log.warn("Failed to record file access for user={} file={}",
                    command.getUserId().value(), command.getFileId().value(), e);
        }
    }
}
