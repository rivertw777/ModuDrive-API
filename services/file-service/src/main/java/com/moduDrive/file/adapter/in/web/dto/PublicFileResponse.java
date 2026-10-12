package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.domain.model.File;

import java.time.LocalDateTime;
import java.util.UUID;

/** Narrower than {@link FileResponse}: no namespaceId, ownerId, path or version id for an
 * anonymous visitor. */
public record PublicFileResponse(
        UUID fileId,
        String name,
        Long fileSize,
        boolean directory,
        LocalDateTime updatedAt
) {
    public static PublicFileResponse from(File file) {
        return new PublicFileResponse(
                file.getId(),
                file.getName(),
                file.getFileSize(),
                file.isDirectory(),
                file.getUpdatedAt()
        );
    }
}
