package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.Role;

import java.util.UUID;

public record FileShareResponse(
        UUID shareId,
        UUID fileId,
        UUID ownerId,
        UUID sharedWithUserId,
        Role role
) {
    public static FileShareResponse from(FileShare fileShare) {
        return new FileShareResponse(
                fileShare.getId(), fileShare.getFileId(),
                fileShare.getOwnerId(), fileShare.getSharedWithUserId(),
                fileShare.getRole()
        );
    }
}
