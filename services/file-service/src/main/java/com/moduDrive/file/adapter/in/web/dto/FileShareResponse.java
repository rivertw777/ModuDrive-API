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
        String sharedWithName,
        /** The directory above the file this grant comes from; null for a direct share. */
        InheritedFrom inheritedFrom
) {
    public record InheritedFrom(UUID fileId, String name) {}

    /** Create/update-role responses, without the grantee's display info. */
    public static FileShareResponse from(FileShare fileShare) {
        return from(fileShare, null, null);
    }

    /** List responses, with the grantee's display info. */
    public static FileShareResponse from(FileShare fileShare, String sharedWithEmail, String sharedWithName) {
        return new FileShareResponse(
                fileShare.getId(), fileShare.getFileId(),
                fileShare.getOwnerId(), fileShare.getSharedWithUserId(),
                fileShare.getRole(), sharedWithEmail, sharedWithName, null
        );
    }

    /** A grant inherited from a directory above the listed file. */
    public static FileShareResponse inherited(FileShare fileShare, String sharedWithEmail, String sharedWithName,
                                              UUID sourceFileId, String sourceName) {
        return new FileShareResponse(
                fileShare.getId(), fileShare.getFileId(),
                fileShare.getOwnerId(), fileShare.getSharedWithUserId(),
                fileShare.getRole(), sharedWithEmail, sharedWithName,
                new InheritedFrom(sourceFileId, sourceName)
        );
    }
}
