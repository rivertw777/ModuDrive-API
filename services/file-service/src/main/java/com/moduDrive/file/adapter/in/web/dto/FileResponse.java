package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.application.port.in.usecase.FileView;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileCategory;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Role;

import java.time.LocalDateTime;
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
        boolean favorite,
        FileCategory category,
        LocalDateTime updatedAt,
        /** "Shared with me" context — all null when the caller owns the file. See {@link FileView}. */
        Role role,
        String sharedByName,
        String sharedByEmail,
        LocalDateTime sharedAt
) {
    public static FileResponse from(File file) {
        return from(FileView.owned(file));
    }

    public static FileResponse from(FileView view) {
        File file = view.file();
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
                file.isFavorite(),
                FileCategory.of(file.getName()),
                file.getUpdatedAt(),
                view.callerRole(),
                view.sharedByName(),
                view.sharedByEmail(),
                view.sharedAt()
        );
    }
}
