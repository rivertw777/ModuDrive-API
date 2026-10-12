package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileId;
import lombok.Getter;

import java.util.UUID;

/** The internal counterpart of {@link GetFileRevisionsCommand}: storage-service resolving the
 * version to download. Carries the original caller's id so FileAccessGuard can check DOWNLOAD. */
@Getter
public class GetLatestFileVersionsCommand {

    private final FileId fileId;
    private final int limit;
    private final UUID callerId;

    public GetLatestFileVersionsCommand(UUID fileId, int limit, UUID callerId) {
        this.fileId = new FileId(fileId);
        this.limit = limit;
        this.callerId = callerId;
    }
}
