package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.storage.application.port.in.command.PublicDownloadFileCommand;
import com.moduDrive.storage.application.port.in.usecase.PublicDownloadFileUseCase;
import com.moduDrive.storage.application.port.out.DownloadQuotaPort;
import com.moduDrive.storage.application.port.out.GetFileVersionPort;
import com.moduDrive.storage.application.port.out.RetrieveBlocksPort;
import com.moduDrive.storage.config.StorageProperties;
import lombok.RequiredArgsConstructor;

import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

/** The anonymous sibling of {@link DownloadFileService}: identical block assembly, but the file
 * is resolved by {@code (fileId, key)} instead of by id + caller, and file-service is the one
 * that decides whether that key still grants access. */
@UseCase
@RequiredArgsConstructor
class PublicDownloadFileService implements PublicDownloadFileUseCase {

    private final GetFileVersionPort getFileVersionPort;
    private final RetrieveBlocksPort retrieveBlocksPort;
    private final DownloadQuotaPort downloadQuotaPort;
    private final StorageProperties storageProperties;

    @Override
    public byte[] downloadPublic(PublicDownloadFileCommand command) {
        GetFileVersionPort.VersionLocation version = locate(command);
        if (command.isInlinePreview()) {
            BlockAssembler.requireWithinInlinePreviewLimit(version.blockCount(), storageProperties.getBlockSize());
        }
        String scope = quotaScope(command.getKey());
        // Anonymous fetches meter per link key: every recipient of one shared link draws on the
        // same window, but a stranger's traffic can't spend the owner's own (user-scoped) quota.
        downloadQuotaPort.checkWithinQuota(scope, version.s3Path());
        List<byte[]> blocks = retrieveBlocksPort.retrieveBlocks(version.s3Path(), version.blockCount());
        byte[] assembled = BlockAssembler.assemble(blocks);
        downloadQuotaPort.recordUsage(scope, version.s3Path(), assembled.length);
        return assembled;
    }

    @Override
    public void downloadPublicStream(PublicDownloadFileCommand command, OutputStream out) {
        GetFileVersionPort.VersionLocation version = locate(command);
        String scope = quotaScope(command.getKey());
        downloadQuotaPort.checkWithinQuota(scope, version.s3Path());
        CountingOutputStream counting = new CountingOutputStream(out);
        try {
            retrieveBlocksPort.streamBlocks(version.s3Path(), version.blockCount(), counting);
        } finally {
            downloadQuotaPort.recordUsage(scope, version.s3Path(), counting.count());
        }
    }

    private GetFileVersionPort.VersionLocation locate(PublicDownloadFileCommand command) {
        return getFileVersionPort.getPublicVersion(command.getFileId(), command.getKey());
    }

    /** Canonical form of the key so re-casing or dropping leading zeros — both of which
     * {@code UUID.fromString} accepts and file-service authorizes identically — can't mint a
     * fresh quota bucket. {@code locate()} has already round-tripped the key through file-service,
     * so it is a well-formed UUID by the time this runs. */
    private static String quotaScope(String key) {
        return UUID.fromString(key).toString();
    }
}
