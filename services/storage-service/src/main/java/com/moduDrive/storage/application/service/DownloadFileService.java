package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.storage.application.port.in.command.DownloadFileCommand;
import com.moduDrive.storage.application.port.in.usecase.DownloadFileUseCase;
import com.moduDrive.storage.application.port.out.DownloadQuotaPort;
import com.moduDrive.storage.application.port.out.GetFileVersionPort;
import com.moduDrive.storage.application.port.out.RetrieveBlocksPort;
import com.moduDrive.storage.config.StorageProperties;
import lombok.RequiredArgsConstructor;

import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

@UseCase
@RequiredArgsConstructor
class DownloadFileService implements DownloadFileUseCase {

    private final GetFileVersionPort getFileVersionPort;
    private final RetrieveBlocksPort retrieveBlocksPort;
    private final DownloadQuotaPort downloadQuotaPort;
    private final StorageProperties storageProperties;

    @Override
    public byte[] download(DownloadFileCommand command) {
        String s3Path = getFileVersionPort.getS3Path(command.getFileId(), command.getUserId());
        int blockCount = getFileVersionPort.getBlockCount(command.getFileId(), command.getUserId());
        if (command.isInlinePreview()) {
            BlockAssembler.requireWithinInlinePreviewLimit(blockCount, storageProperties.getBlockSize());
        }
        charge(command.getUserId(), s3Path, blockCount);
        List<byte[]> blocks = retrieveBlocksPort.retrieveBlocks(s3Path, blockCount);
        return BlockAssembler.assemble(blocks);
    }

    @Override
    public void downloadStream(DownloadFileCommand command, OutputStream out) {
        String s3Path = getFileVersionPort.getS3Path(command.getFileId(), command.getUserId());
        int blockCount = getFileVersionPort.getBlockCount(command.getFileId(), command.getUserId());
        charge(command.getUserId(), s3Path, blockCount);
        retrieveBlocksPort.streamBlocks(s3Path, blockCount, out);
    }

    /** Meter this fetch against the file's per-user download quota; throws once the window is
     * spent. Inline preview counts too — same bytes leave the building either way. */
    private void charge(UUID userId, String s3Path, int blockCount) {
        downloadQuotaPort.recordAndEnforce(
                userId.toString(), s3Path, (long) blockCount * storageProperties.getBlockSize());
    }
}
