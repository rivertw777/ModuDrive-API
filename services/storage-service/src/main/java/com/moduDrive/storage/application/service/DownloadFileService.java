package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.storage.application.port.in.command.DownloadFileCommand;
import com.moduDrive.storage.application.port.in.usecase.DownloadFileUseCase;
import com.moduDrive.storage.application.port.out.DownloadQuotaPort;
import com.moduDrive.storage.application.port.out.GetFileVersionPort;
import com.moduDrive.storage.application.port.out.RetrieveBlocksPort;
import org.springframework.beans.factory.annotation.Value;

import java.io.OutputStream;
import java.util.List;

@UseCase
class DownloadFileService implements DownloadFileUseCase {

    private final GetFileVersionPort getFileVersionPort;
    private final RetrieveBlocksPort retrieveBlocksPort;
    private final DownloadQuotaPort downloadQuotaPort;
    private final int blockSize;

    DownloadFileService(GetFileVersionPort getFileVersionPort,
                        RetrieveBlocksPort retrieveBlocksPort,
                        DownloadQuotaPort downloadQuotaPort,
                        @Value("${storage.block-size}") int blockSize) {
        this.getFileVersionPort = getFileVersionPort;
        this.retrieveBlocksPort = retrieveBlocksPort;
        this.downloadQuotaPort = downloadQuotaPort;
        this.blockSize = blockSize;
    }

    @Override
    public byte[] download(DownloadFileCommand command) {
        String scope = command.getUserId().toString();
        // Only an inline preview counts as "opening" the file for the recent list (Drive-style).
        var version = getFileVersionPort.getLatestVersion(
                command.getFileId(), command.getUserId(), command.isInlinePreview());
        String s3Path = version.s3Path();
        int blockCount = version.blockCount();
        if (command.isInlinePreview()) {
            BlockAssembler.requireWithinInlinePreviewLimit(blockCount, blockSize);
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
        // A plain download never touches "recent" — markAccessed=false.
        var version = getFileVersionPort.getLatestVersion(command.getFileId(), command.getUserId(), false);
        String s3Path = version.s3Path();
        int blockCount = version.blockCount();
        downloadQuotaPort.checkWithinQuota(scope, s3Path);
        CountingOutputStream counting = new CountingOutputStream(out);
        try {
            retrieveBlocksPort.streamBlocks(s3Path, blockCount, counting);
        } finally {
            downloadQuotaPort.recordUsage(scope, s3Path, counting.count());
        }
    }
}
