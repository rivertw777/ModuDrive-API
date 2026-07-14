package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.Permission;

import java.util.UUID;

public record FileShareResponse(
        UUID shareId,
        UUID fileId,
        Long ownerId,
        Long sharedWithUserId,
        Permission permission
) {
    public static FileShareResponse from(FileShare fileShare) {
        return new FileShareResponse(
                fileShare.getId(), fileShare.getFileId(),
                fileShare.getOwnerId(), fileShare.getSharedWithUserId(),
                fileShare.getPermission()
        );
    }
}
