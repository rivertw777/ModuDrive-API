package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.storage.application.port.in.command.PublicDownloadFileCommand;
import com.moduDrive.storage.application.port.in.usecase.PublicDownloadFileUseCase;
import com.moduDrive.storage.application.port.out.DownloadQuotaPort;
import com.moduDrive.storage.application.port.out.GetFileVersionPort;
import com.moduDrive.storage.application.port.out.RetrieveBlocksPort;
import org.springframework.beans.factory.annotation.Value;

import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

/** The anonymous sibling of {@link DownloadFileService}: identical block assembly, but the file
 * is resolved by {@code (fileId, key)} instead of by id + caller, and file-service is the one
 * that decides whether that key still grants access. */
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
            BlockAssembler.requireWithinInlinePreviewLimit(version.blockCount(), blockSize);
        }
        String scope = quotaScope(command);
        // Anonymous fetches meter per file: every visitor who reaches it — however they got in —
        // draws on the same window, but a stranger's traffic can't spend the owner's own
        // (user-scoped) quota.
        downloadQuotaPort.checkWithinQuota(scope, version.s3Path());
        List<byte[]> blocks = retrieveBlocksPort.retrieveBlocks(version.s3Path(), version.blockCount());
        byte[] assembled = BlockAssembler.assemble(blocks);
        downloadQuotaPort.recordUsage(scope, version.s3Path(), assembled.length);
        return assembled;
    }

    @Override
    public void downloadPublicStream(PublicDownloadFileCommand command, OutputStream out) {
        GetFileVersionPort.VersionLocation version = locate(command);
        String scope = quotaScope(command);
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

    /** Always the file itself, never {@code key} — only file-service knows which grant actually
     * authorized a given request, and for a LINK-scoped file it never even looks at {@code key}
     * (see {@code PublicFileResolver.resolve}, which short-circuits on {@code linkRole} before
     * {@code matchesGuestInvite} is evaluated). Trusting a present {@code key} as a per-person
     * meter would let anyone mint a fresh, always-empty bucket on every request just by attaching
     * a random well-formed UUID as {@code ?key=} — the other half of issue #312's root cause (the
     * first half was the plain NPE on a missing key; this is what patching only that half would
     * have left standing). Metering by {@code fileId} instead is unforgeable: every anonymous
     * visitor of one file, however they got in, draws on the same window. {@code locate()} has
     * already round-tripped {@code fileId} through file-service by the time this runs, so it is a
     * well-formed UUID; re-casing or dropping leading zeros — both of which {@code UUID.fromString}
     * accepts and file-service authorizes identically — can't mint a second bucket for the same
     * file either. */
    private static String quotaScope(PublicDownloadFileCommand command) {
        return "public:" + UUID.fromString(command.getFileId());
    }
}
