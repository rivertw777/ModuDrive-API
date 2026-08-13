package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare.FileShareOwnerId;
import com.moduDrive.file.domain.model.FileShare.FileShareRole;
import com.moduDrive.file.domain.model.Role;
import lombok.Getter;

import java.util.UUID;

@Getter
public class ShareFileCommand {

    private final FileId fileId;
    private final FileShareOwnerId ownerId;
    private final String email;
    private final FileShareRole role;

    public ShareFileCommand(UUID fileId, UUID ownerId, String email, Role role) {
        this.fileId = new FileId(fileId);
        this.ownerId = new FileShareOwnerId(ownerId);
        this.email = email;
        this.role = new FileShareRole(role);
    }
}
