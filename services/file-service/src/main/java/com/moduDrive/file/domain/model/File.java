package com.moduDrive.file.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class File {

    private final UUID id;
    private final UUID namespaceId;
    private String name;
    private String path;
    private final UUID ownerId;
    private UUID currentVersionId;
    private Long fileSize;
    private FileStatus status;
    private final boolean directory;
    private boolean favorite;
    private LocalDateTime updatedAt;
    private ShareScope accessScope;
    private UUID linkToken;

    public static File create(FileNamespaceId namespaceId,
                              FileName name,
                              FilePath path,
                              FileOwnerId ownerId,
                              FileIsDirectory isDirectory) {
        return new File(
                null,
                namespaceId.value(),
                name.value(),
                path.value(),
                ownerId.value(),
                null,
                null,
                FileStatus.PENDING,
                isDirectory.value(),
                false,
                null,
                ShareScope.RESTRICTED,
                null
        );
    }

    public static File createDirectory(FileNamespaceId namespaceId,
                                       FileName name,
                                       FilePath path,
                                       FileOwnerId ownerId) {
        return new File(
                null,
                namespaceId.value(),
                name.value(),
                path.value(),
                ownerId.value(),
                null,
                0L,
                FileStatus.UPLOADED,
                true,
                false,
                null,
                ShareScope.RESTRICTED,
                null
        );
    }

    public static File withId(FileId id,
                              FileNamespaceId namespaceId,
                              FileName name,
                              FilePath path,
                              FileOwnerId ownerId,
                              FileCurrentVersionId currentVersionId,
                              FileSize fileSize,
                              FileStatus status,
                              FileIsDirectory isDirectory) {
        return new File(
                id.value(),
                namespaceId.value(),
                name.value(),
                path.value(),
                ownerId.value(),
                currentVersionId != null ? currentVersionId.value() : null,
                fileSize != null ? fileSize.value() : null,
                status,
                isDirectory.value(),
                false,
                null,
                ShareScope.RESTRICTED,
                null
        );
    }

    public void markUploaded(UUID versionId, Long size) {
        this.status = FileStatus.UPLOADED;
        this.currentVersionId = versionId;
        this.fileSize = size;
    }

    public void softDelete() {
        this.status = FileStatus.DELETED;
    }

    public void restore() {
        this.status = FileStatus.UPLOADED;
    }

    public void rename(FileName name) {
        this.name = name.value();
    }

    public void move(FilePath path) {
        this.path = path.value();
    }

    /** This entry's own full path (its parent {@code path} joined with its {@code name}) — the
     * value child entries store as their {@code path} while they live inside it. */
    public String fullPath() {
        return "/".equals(path) ? "/" + name : path + "/" + name;
    }

    public void markFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public void markUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** Idempotent: an already-issued token is kept, so re-selecting LINK never invalidates
     * links already handed out. Rotation would need its own explicit operation. */
    public void enableLinkSharing(UUID token) {
        this.accessScope = ShareScope.LINK;
        if (this.linkToken == null) {
            this.linkToken = token;
        }
    }

    public void disableLinkSharing() {
        this.accessScope = ShareScope.RESTRICTED;
        this.linkToken = null;
    }

    public record FileId(UUID value) {}
    public record FileNamespaceId(UUID value) {}
    public record FileName(String value) {}
    public record FilePath(String value) {}
    public record FileOwnerId(UUID value) {}
    public record FileCurrentVersionId(UUID value) {}
    public record FileSize(Long value) {}
    public record FileIsDirectory(boolean value) {}
}
