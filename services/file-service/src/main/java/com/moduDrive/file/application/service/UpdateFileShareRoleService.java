package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.UpdateFileShareRoleCommand;
import com.moduDrive.file.application.port.in.usecase.UpdateFileShareRoleUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.SaveFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
class UpdateFileShareRoleService implements UpdateFileShareRoleUseCase {

    private final FindFilePort findFilePort;
    private final FindFileSharePort findFileSharePort;
    private final SaveFileSharePort saveFileSharePort;
    private final FileAccessGuard fileAccessGuard;

    @Transactional
    @Override
    public FileShare updateFileShareRole(UpdateFileShareRoleCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        fileAccessGuard.requireOwner(file, command.getCallerId());

        FileShare fileShare = findShareOfFile(command);
        fileShare.changeRole(command.getRole());

        return saveFileSharePort.saveFileShare(fileShare);
    }

    /** A share id belonging to a different file must read as "not found" here — otherwise the
     * owner of file A could edit shares on file B just by guessing a share id. */
    private FileShare findShareOfFile(UpdateFileShareRoleCommand command) {
        return findFileSharePort.findByShareId(command.getShareId())
                .filter(share -> share.getFileId().equals(command.getFileId().value()))
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_SHARE_NOT_FOUND));
    }
}
