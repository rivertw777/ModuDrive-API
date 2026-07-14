package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare.FileShareOwnerId;
import com.moduDrive.file.domain.model.FileShare.FileSharePermission;
import com.moduDrive.file.domain.model.FileShare.FileShareSharedWithUserId;
import com.moduDrive.file.domain.model.Permission;
import lombok.Getter;

import java.util.UUID;

@Getter
public class ShareFileCommand {

    private final FileId fileId;
    private final FileShareOwnerId ownerId;
    private final FileShareSharedWithUserId sharedWithUserId;
    private final FileSharePermission permission;

    public ShareFileCommand(UUID fileId, Long ownerId, Long sharedWithUserId, Permission permission) {
        this.fileId = new FileId(fileId);
        this.ownerId = new FileShareOwnerId(ownerId);
        this.sharedWithUserId = new FileShareSharedWithUserId(sharedWithUserId);
        this.permission = new FileSharePermission(permission);
    }
}
