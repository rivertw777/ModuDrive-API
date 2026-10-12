package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.PurgeFileCommand;
import com.moduDrive.file.application.port.in.usecase.PurgeFileUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
class PurgeFileService implements PurgeFileUseCase {

    private final FindFilePort findFilePort;
    private final FileAccessGuard fileAccessGuard;
    private final FilePurger filePurger;

    // Not @Transactional: filePurger.purgeRoot opens its own (REQUIRES_NEW); an outer one would
    // only hold a second connection.
    @Override
    public void purgeFile(PurgeFileCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        fileAccessGuard.requireOwner(file, command.getCallerId());

        if (file.getStatus() != FileStatus.TRASHED) {
            throw new BusinessException(FileExceptionCase.FILE_NOT_DELETED);
        }

        filePurger.purgeRoot(file, command.getCallerId());
    }
}
