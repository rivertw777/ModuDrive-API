package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.storage.application.port.in.command.PurgeStoredFileCommand;
import com.moduDrive.storage.application.port.in.usecase.PurgeStoredFileUseCase;
import com.moduDrive.storage.application.port.out.DeleteBlocksPort;
import com.moduDrive.storage.application.port.out.GetFileVersionPort;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
class PurgeStoredFileService implements PurgeStoredFileUseCase {

    private final GetFileVersionPort getFileVersionPort;
    private final DeleteBlocksPort deleteBlocksPort;

    @Override
    public void purgeStoredFile(PurgeStoredFileCommand command) {
        getFileVersionPort.getAllVersions(command.getFileId(), command.getUserId())
                .forEach(version -> deleteBlocksPort.deleteBlocks(version.s3Path(), version.blockCount()));
    }
}
