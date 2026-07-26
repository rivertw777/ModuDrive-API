package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.MoveFileCommand;
import com.moduDrive.file.application.port.in.usecase.MoveFileUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
class MoveFileService implements MoveFileUseCase {

    private final FindFilePort findFilePort;
    private final SaveFilePort saveFilePort;

    @Transactional
    @Override
    public File moveFile(MoveFileCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));

        if (file.getStatus() == FileStatus.DELETED) {
            throw new BusinessException(FileExceptionCase.FILE_ALREADY_DELETED);
        }

        file.move(command.getPath());
        return saveFilePort.saveFile(file);
    }
}
