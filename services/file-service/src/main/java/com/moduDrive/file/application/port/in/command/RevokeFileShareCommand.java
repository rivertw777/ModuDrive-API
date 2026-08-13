package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare.FileShareId;
import lombok.Getter;

import java.util.UUID;

@Getter
public class RevokeFileShareCommand {

    private final FileId fileId;
    private final FileShareId shareId;
    private final UUID callerId;

    public RevokeFileShareCommand(UUID fileId, UUID shareId, UUID callerId) {
        this.fileId = new FileId(fileId);
        this.shareId = new FileShareId(shareId);
        this.callerId = callerId;
    }
}
