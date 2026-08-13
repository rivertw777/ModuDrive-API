package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.domain.model.File;

import java.time.LocalDateTime;
import java.util.UUID;

/** Deliberately narrower than {@link FileResponse}: an anonymous link visitor has no business
 * seeing namespaceId, ownerId, path, or the internal version id. */
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
