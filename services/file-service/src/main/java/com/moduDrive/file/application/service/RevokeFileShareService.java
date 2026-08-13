package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.RevokeFileShareCommand;
import com.moduDrive.file.application.port.in.usecase.RevokeFileShareUseCase;
import com.moduDrive.file.application.port.out.DeleteFileSharePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
class RevokeFileShareService implements RevokeFileShareUseCase {

    private final FindFilePort findFilePort;
    private final FindFileSharePort findFileSharePort;
    private final DeleteFileSharePort deleteFileSharePort;
    private final FileAccessGuard fileAccessGuard;

    @Transactional
    @Override
    public void revokeFileShare(RevokeFileShareCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        fileAccessGuard.requireOwner(file, command.getCallerId());

        // Same reason as UpdateFileShareRoleService: a share id from another file must not be
        // revocable by this file's owner.
        FileShare fileShare = findFileSharePort.findByShareId(command.getShareId())
                .filter(share -> share.getFileId().equals(command.getFileId().value()))
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_SHARE_NOT_FOUND));

        deleteFileSharePort.deleteFileShare(new FileShare.FileShareId(fileShare.getId()));
    }
}
