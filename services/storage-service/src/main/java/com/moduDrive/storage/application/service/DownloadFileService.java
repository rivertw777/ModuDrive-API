package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.storage.application.port.in.command.DownloadFileCommand;
import com.moduDrive.storage.application.port.in.usecase.DownloadFileUseCase;
import com.moduDrive.storage.application.port.in.usecase.RangeContent;
import com.moduDrive.storage.application.port.out.DownloadQuotaPort;
import com.moduDrive.storage.application.port.out.GetFileVersionPort;
import com.moduDrive.storage.application.port.out.RetrieveBlocksPort;
import org.springframework.beans.factory.annotation.Value;

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
        if (command.isInlinePreview()) {
            BlockAssembler.requireWithinInlinePreviewLimit(version.blockKeys().size(), blockSize);
        }
        downloadQuotaPort.checkWithinQuota(scope, version.versionKey());
        List<byte[]> blocks = retrieveBlocksPort.retrieveBlocks(version.blockKeys());
        byte[] assembled = BlockAssembler.assemble(blocks);
        // Inline preview counts too — same bytes leave the building either way.
        downloadQuotaPort.recordUsage(scope, version.versionKey(), assembled.length);
        return assembled;
    }

    @Override
    public RangeContent viewRange(DownloadFileCommand command, long start, Long end) {
        var version = getFileVersionPort.getLatestVersion(
                command.getFileId(), command.getUserId(), command.isInlinePreview());
        return BlockAssembler.readRange(retrieveBlocksPort, downloadQuotaPort,
                command.getUserId().toString(), version, blockSize, start, end);
    }
}
