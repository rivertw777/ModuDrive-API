package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.Role;

import java.util.UUID;

public record FileShareResponse(
        UUID shareId,
        UUID fileId,
        UUID ownerId,
        UUID sharedWithUserId,
        Role role,
        String sharedWithEmail,
        String sharedWithName
) {
    /** Create/update-role responses: the caller already knows who they just acted on, so
     * enrichment is skipped rather than spending an extra member-service round trip. */
    public static FileShareResponse from(FileShare fileShare) {
        return from(fileShare, null, null);
    }

    /** List responses enrich each row with the accessor's display info (see #156). */
    public static FileShareResponse from(FileShare fileShare, String sharedWithEmail, String sharedWithName) {
        return new FileShareResponse(
                fileShare.getId(), fileShare.getFileId(),
                fileShare.getOwnerId(), fileShare.getSharedWithUserId(),
                fileShare.getRole(), sharedWithEmail, sharedWithName
        );
    }
}
