package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.storage.application.port.in.command.DownloadFileCommand;
import com.moduDrive.storage.application.port.in.usecase.DownloadFileUseCase;
import com.moduDrive.storage.application.port.out.GetFileVersionPort;
import com.moduDrive.storage.application.port.out.RetrieveBlocksPort;
import lombok.RequiredArgsConstructor;

import java.util.List;

@UseCase
@RequiredArgsConstructor
class DownloadFileService implements DownloadFileUseCase {

    private final GetFileVersionPort getFileVersionPort;
    private final RetrieveBlocksPort retrieveBlocksPort;

    @Override
    public byte[] download(DownloadFileCommand command) {
        String s3Path = getFileVersionPort.getS3Path(command.getFileId(), command.getUserId());
        int blockCount = getFileVersionPort.getBlockCount(command.getFileId(), command.getUserId());
        List<byte[]> blocks = retrieveBlocksPort.retrieveBlocks(s3Path, blockCount);
        return BlockAssembler.assemble(blocks);
    }
}
