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
    private final UUID ownerId;
    private final UUID sharedWithUserId;
    private Role role;

    public static FileShare create(FileShareFileId fileId,
                                   FileShareOwnerId ownerId,
                                   FileShareSharedWithUserId sharedWithUserId,
                                   FileShareRole role) {
        return new FileShare(null, fileId.value(), ownerId.value(), sharedWithUserId.value(), role.value());
    }

    public static FileShare withId(FileShareId id,
                                   FileShareFileId fileId,
                                   FileShareOwnerId ownerId,
                                   FileShareSharedWithUserId sharedWithUserId,
                                   FileShareRole role) {
        return new FileShare(id.value(), fileId.value(), ownerId.value(), sharedWithUserId.value(), role.value());
    }

    public void changeRole(FileShareRole role) {
        this.role = role.value();
    }

    public record FileShareId(UUID value) {}
    public record FileShareFileId(UUID value) {}
    public record FileShareOwnerId(UUID value) {}
    public record FileShareSharedWithUserId(UUID value) {}
    public record FileShareRole(Role value) {}
}
