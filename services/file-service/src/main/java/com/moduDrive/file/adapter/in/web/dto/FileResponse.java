package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileStatus;

import java.util.UUID;

public record FileResponse(
        UUID fileId,
        UUID namespaceId,
        String name,
        String path,
        UUID ownerId,
        UUID currentVersionId,
        Long fileSize,
        FileStatus status,
        boolean directory,
        boolean favorite
) {
    public static FileResponse from(File file) {
        return new FileResponse(
                file.getId(),
                file.getNamespaceId(),
                file.getName(),
                file.getPath(),
                file.getOwnerId(),
                file.getCurrentVersionId(),
                file.getFileSize(),
                file.getStatus(),
                file.isDirectory(),
                file.isFavorite()
        );
    }
}
