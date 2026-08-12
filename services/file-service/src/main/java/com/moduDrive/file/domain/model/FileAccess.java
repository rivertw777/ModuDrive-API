package com.moduDrive.file.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Records that a user opened a file, so "recent files" can be listed per viewer — separate
 * from {@link File#isFavorite()}, which is owner-scoped and lives on the namespace owner's
 * own file row. A shared file can be "recent" independently for each of its viewers. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FileAccess {

    private final UUID userId;
    private final UUID fileId;
    private final LocalDateTime accessedAt;

    public static FileAccess of(FileAccessUserId userId, FileAccessFileId fileId, LocalDateTime accessedAt) {
        return new FileAccess(userId.value(), fileId.value(), accessedAt);
    }

    public record FileAccessUserId(UUID value) {}
    public record FileAccessFileId(UUID value) {}
}
