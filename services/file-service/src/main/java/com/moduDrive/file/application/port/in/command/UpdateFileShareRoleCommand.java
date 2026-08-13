package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare.FileShareId;
import com.moduDrive.file.domain.model.FileShare.FileShareRole;
import com.moduDrive.file.domain.model.Role;
import lombok.Getter;

import java.util.UUID;

@Getter
public class UpdateFileShareRoleCommand {

    private final FileId fileId;
    private final FileShareId shareId;
    private final UUID callerId;
    private final FileShareRole role;

    public UpdateFileShareRoleCommand(UUID fileId, UUID shareId, UUID callerId, Role role) {
        this.fileId = new FileId(fileId);
        this.shareId = new FileShareId(shareId);
        this.callerId = callerId;
        this.role = new FileShareRole(role);
    }
}
