package com.moduDrive.file.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FileShare {

    private final UUID id;
    private final UUID fileId;
    private final Long ownerId;
    private final Long sharedWithUserId;
    private final Permission permission;

    public static FileShare create(FileShareFileId fileId,
                                   FileShareOwnerId ownerId,
                                   FileShareSharedWithUserId sharedWithUserId,
                                   FileSharePermission permission) {
        return new FileShare(null, fileId.value(), ownerId.value(), sharedWithUserId.value(), permission.value());
    }

    public static FileShare withId(FileShareId id,
                                   FileShareFileId fileId,
                                   FileShareOwnerId ownerId,
                                   FileShareSharedWithUserId sharedWithUserId,
                                   FileSharePermission permission) {
        return new FileShare(id.value(), fileId.value(), ownerId.value(), sharedWithUserId.value(), permission.value());
    }

    public record FileShareId(UUID value) {}
    public record FileShareFileId(UUID value) {}
    public record FileShareOwnerId(Long value) {}
    public record FileShareSharedWithUserId(Long value) {}
    public record FileSharePermission(Permission value) {}
}
