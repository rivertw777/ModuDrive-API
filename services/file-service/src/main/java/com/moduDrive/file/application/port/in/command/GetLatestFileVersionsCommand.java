package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileId;
import lombok.Getter;

import java.util.UUID;

/** This is the internal, service-to-service counterpart of {@link GetFileRevisionsCommand} used
 * by storage-service to resolve the latest version for download. Kept off the tenant-facing
 * {@code /api/v1/files/**} prefix, but still carries the original caller's id so FileAccessGuard
 * can enforce the same VIEWER check the tenant-facing read path does (see #152). */
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
