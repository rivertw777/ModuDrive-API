package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.storage.application.port.in.command.PublicDownloadFileCommand;
import com.moduDrive.storage.application.port.in.usecase.PublicDownloadFileUseCase;
import com.moduDrive.storage.application.port.in.usecase.RangeContent;
import com.moduDrive.storage.application.port.out.DownloadQuotaPort;
import com.moduDrive.storage.application.port.out.GetFileVersionPort;
import com.moduDrive.storage.application.port.out.RetrieveBlocksPort;
import org.springframework.beans.factory.annotation.Value;

import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

/** Anonymous counterpart of {@link DownloadFileService}: the file is resolved by {@code (fileId, key)}
 * and file-service decides whether that still grants access. */
@UseCase
class PublicDownloadFileService implements PublicDownloadFileUseCase {

    private final GetFileVersionPort getFileVersionPort;
    private final RetrieveBlocksPort retrieveBlocksPort;
    private final DownloadQuotaPort downloadQuotaPort;
    private final int blockSize;

    PublicDownloadFileService(GetFileVersionPort getFileVersionPort,
                              RetrieveBlocksPort retrieveBlocksPort,
                              DownloadQuotaPort downloadQuotaPort,
                              @Value("${storage.block-size}") int blockSize) {
        this.getFileVersionPort = getFileVersionPort;
        this.retrieveBlocksPort = retrieveBlocksPort;
        this.downloadQuotaPort = downloadQuotaPort;
        this.blockSize = blockSize;
    }

    @Override
    public byte[] downloadPublic(PublicDownloadFileCommand command) {
        GetFileVersionPort.VersionLocation version = locate(command);
        if (command.isInlinePreview()) {
            BlockAssembler.requireWithinInlinePreviewLimit(version.blockKeys().size(), blockSize);
        }
        String scope = quotaScope(command);
        downloadQuotaPort.checkWithinQuota(scope, version.versionKey());
        List<byte[]> blocks = retrieveBlocksPort.retrieveBlocks(version.blockKeys());
        byte[] assembled = BlockAssembler.assemble(blocks);
        downloadQuotaPort.recordUsage(scope, version.versionKey(), assembled.length);
        return assembled;
    }

    @Override
    public RangeContent viewPublicRange(PublicDownloadFileCommand command, long start, Long end) {
        return BlockAssembler.readRange(retrieveBlocksPort, downloadQuotaPort,
                quotaScope(command), locate(command), blockSize, start, end);
    }

    @Override
    public void downloadPublicStream(PublicDownloadFileCommand command, OutputStream out) {
        GetFileVersionPort.VersionLocation version = locate(command);
        String scope = quotaScope(command);
        downloadQuotaPort.checkWithinQuota(scope, version.versionKey());
        CountingOutputStream counting = new CountingOutputStream(out);
        try {
            retrieveBlocksPort.streamBlocks(version.blockKeys(), counting);
        } finally {
            downloadQuotaPort.recordUsage(scope, version.versionKey(), counting.count());
        }
    }

    private GetFileVersionPort.VersionLocation locate(PublicDownloadFileCommand command) {
        return getFileVersionPort.getPublicVersion(command.getFileId(), command.getKey());
    }

    /** Always the file, never {@code key}: file-service ignores {@code key} for a LINK-scoped file, so
     * metering by key would let anyone get a fresh bucket per request with a random {@code ?key=}.
     * Every anonymous visitor of one file shares one window, and the owner's user-scoped quota is
     * separate. {@code locate()} has already validated {@code fileId}; normalizing it through
     * {@code UUID.fromString} keeps re-cased spellings from minting a second bucket. */
    private static String quotaScope(PublicDownloadFileCommand command) {
        return "public:" + UUID.fromString(command.getFileId());
    }
}
