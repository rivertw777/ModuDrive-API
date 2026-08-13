package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.RenameFileCommand;
import com.moduDrive.file.application.port.in.usecase.RenameFileUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
class RenameFileService implements RenameFileUseCase {

    private final FindFilePort findFilePort;
    private final SaveFilePort saveFilePort;
    private final DirectoryCascader directoryCascader;
    private final FileAccessGuard fileAccessGuard;

    @Transactional
    @Override
    public File renameFile(RenameFileCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        fileAccessGuard.requireRole(file, command.getCallerId(), Role.EDITOR);

        if (file.getStatus() == FileStatus.DELETED) {
            throw new BusinessException(FileExceptionCase.FILE_ALREADY_DELETED);
        }

        String oldFullPath = file.fullPath();
        file.rename(command.getName());
        File saved = saveFilePort.saveFile(file);

        if (saved.isDirectory()) {
            directoryCascader.movePath(new NamespaceId(saved.getNamespaceId()), oldFullPath, saved.fullPath());
        }

        return saved;
    }
}
