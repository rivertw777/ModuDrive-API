package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileId;
import lombok.Getter;

import java.util.UUID;

/** Internal, service-to-service counterpart used by storage-service to resolve every version to
 * purge — deliberately checks ownership (see {@link com.moduDrive.file.application.service.FileAccessGuard#requireOwner})
 * rather than a delegated permission: a purge destroys data, and per this codebase's own
 * invariant (see {@code Permission}), destructive actions are never delegated to a share
 * grantee, only the owner. The caller (storage-service) always passes the file's own owner id
 * (see {@code PurgeStorageBlocksPort}), so in practice this can't fail today — it's defence in
 * depth against ever reusing the DOWNLOAD-permission-gated revisions lookup here instead, which
 * a VIEWER share also holds and would let it trigger permanent data loss. The real boundary
 * keeping an arbitrary caller out is that {@code /internal/**} isn't gateway-routed. */
@Getter
public class GetAllFileVersionsCommand {

    private final FileId fileId;
    private final UUID callerId;

    public GetAllFileVersionsCommand(UUID fileId, UUID callerId) {
        this.fileId = new FileId(fileId);
        this.callerId = callerId;
    }
}
