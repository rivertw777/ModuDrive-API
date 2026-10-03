package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.storage.application.port.in.command.PurgeStoredFileCommand;
import com.moduDrive.storage.application.port.in.usecase.PurgeStoredFileUseCase;
import com.moduDrive.storage.application.port.out.DeleteBlocksPort;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
class PurgeStoredFileService implements PurgeStoredFileUseCase {

    private final DeleteBlocksPort deleteBlocksPort;

    @Override
    public void purgeStoredFile(PurgeStoredFileCommand command) {
        command.getVersions().forEach(version -> deleteBlocksPort.deleteBlocks(version.s3Path(), version.blockCount()));
    }
}
