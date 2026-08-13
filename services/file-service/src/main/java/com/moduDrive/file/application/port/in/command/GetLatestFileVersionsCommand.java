package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileId;
import lombok.Getter;

import java.util.UUID;

/** No caller id: this is the internal, service-to-service counterpart of
 * {@link GetFileRevisionsCommand} used by storage-service to resolve the latest version for
 * download — there is no end user in that call, so there is nothing for FileAccessGuard to
 * check. Kept off the tenant-facing {@code /api/v1/files/**} prefix for that reason. */
@Getter
public class GetLatestFileVersionsCommand {

    private final FileId fileId;
    private final int limit;

    public GetLatestFileVersionsCommand(UUID fileId, int limit) {
        this.fileId = new FileId(fileId);
        this.limit = limit;
    }
}
