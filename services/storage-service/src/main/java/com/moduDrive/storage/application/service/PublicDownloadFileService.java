package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.storage.application.port.in.command.PublicDownloadFileCommand;
import com.moduDrive.storage.application.port.in.usecase.PublicDownloadFileUseCase;
import com.moduDrive.storage.application.port.out.GetFileVersionPort;
import com.moduDrive.storage.application.port.out.RetrieveBlocksPort;
import com.moduDrive.storage.config.StorageProperties;
import lombok.RequiredArgsConstructor;

import java.io.OutputStream;
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
        GetFileVersionPort.VersionLocation version = locate(command);
        if (command.isInlinePreview()) {
            BlockAssembler.requireWithinInlinePreviewLimit(version.blockCount(), storageProperties.getBlockSize());
        }
        List<byte[]> blocks = retrieveBlocksPort.retrieveBlocks(version.s3Path(), version.blockCount());
        return BlockAssembler.assemble(blocks);
    }

    @Override
    public void downloadPublicStream(PublicDownloadFileCommand command, OutputStream out) {
        GetFileVersionPort.VersionLocation version = locate(command);
        retrieveBlocksPort.streamBlocks(version.s3Path(), version.blockCount(), out);
    }

    /** A folder link token needs the descendant's id to pick a file; a direct file link doesn't. */
    private GetFileVersionPort.VersionLocation locate(PublicDownloadFileCommand command) {
        if (command.hasEntry()) {
            return getFileVersionPort.getPublicDescendantVersion(command.getToken(), command.getEntryId());
        }
        return new GetFileVersionPort.VersionLocation(
                getFileVersionPort.getPublicS3Path(command.getToken()),
                getFileVersionPort.getPublicBlockCount(command.getToken()));
    }
}
