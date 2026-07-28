package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.RestoreFileCommand;
import com.moduDrive.file.application.port.in.usecase.RestoreFileUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
class RestoreFileService implements RestoreFileUseCase {

    private final FindFilePort findFilePort;
    private final SaveFilePort saveFilePort;

    @Transactional
    @Override
    public File restoreFile(RestoreFileCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));

        if (file.getStatus() != FileStatus.DELETED) {
            throw new BusinessException(FileExceptionCase.FILE_NOT_DELETED);
        }

        file.restore();
        return saveFilePort.saveFile(file);
    }
}
