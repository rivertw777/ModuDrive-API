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

@UseCase
@RequiredArgsConstructor
class DownloadFileService implements DownloadFileUseCase {

    private final GetFileVersionPort getFileVersionPort;
    private final RetrieveBlocksPort retrieveBlocksPort;
    private final DownloadQuotaPort downloadQuotaPort;
    private final StorageProperties storageProperties;

    @Override
    public byte[] download(DownloadFileCommand command) {
        String scope = command.getUserId().toString();
        String s3Path = getFileVersionPort.getS3Path(command.getFileId(), command.getUserId());
        int blockCount = getFileVersionPort.getBlockCount(command.getFileId(), command.getUserId());
        if (command.isInlinePreview()) {
            BlockAssembler.requireWithinInlinePreviewLimit(blockCount, storageProperties.getBlockSize());
        }
        downloadQuotaPort.checkWithinQuota(scope, s3Path);
        List<byte[]> blocks = retrieveBlocksPort.retrieveBlocks(s3Path, blockCount);
        byte[] assembled = BlockAssembler.assemble(blocks);
        // Inline preview counts too — same bytes leave the building either way.
        downloadQuotaPort.recordUsage(scope, s3Path, assembled.length);
        return assembled;
    }

    @Override
    public void downloadStream(DownloadFileCommand command, OutputStream out) {
        String scope = command.getUserId().toString();
        String s3Path = getFileVersionPort.getS3Path(command.getFileId(), command.getUserId());
        int blockCount = getFileVersionPort.getBlockCount(command.getFileId(), command.getUserId());
        downloadQuotaPort.checkWithinQuota(scope, s3Path);
        CountingOutputStream counting = new CountingOutputStream(out);
        try {
            retrieveBlocksPort.streamBlocks(s3Path, blockCount, counting);
        } finally {
            downloadQuotaPort.recordUsage(scope, s3Path, counting.count());
        }
    }
}
