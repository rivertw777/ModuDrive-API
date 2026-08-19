package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.storage.application.port.in.command.PublicDownloadFileCommand;
import com.moduDrive.storage.application.port.in.usecase.PublicDownloadFileUseCase;
import com.moduDrive.storage.application.port.out.GetFileVersionPort;
import com.moduDrive.storage.application.port.out.RetrieveBlocksPort;
import com.moduDrive.storage.config.StorageProperties;
import lombok.RequiredArgsConstructor;

import java.util.List;

/** The anonymous sibling of {@link DownloadFileService}: identical block assembly, but the file is
 * resolved by link token instead of by id + caller, and file-service is the one that decides
 * whether that token still grants access. */
@UseCase
@RequiredArgsConstructor
class PublicDownloadFileService implements PublicDownloadFileUseCase {

    private final GetFileVersionPort getFileVersionPort;
    private final RetrieveBlocksPort retrieveBlocksPort;
    private final StorageProperties storageProperties;

    @Override
    public byte[] downloadPublic(PublicDownloadFileCommand command) {
        String s3Path = getFileVersionPort.getPublicS3Path(command.getToken());
        int blockCount = getFileVersionPort.getPublicBlockCount(command.getToken());
        if (command.isInlinePreview()) {
            BlockAssembler.requireWithinInlinePreviewLimit(blockCount, storageProperties.getBlockSize());
        }
        List<byte[]> blocks = retrieveBlocksPort.retrieveBlocks(s3Path, blockCount);
        return BlockAssembler.assemble(blocks);
    }
}
