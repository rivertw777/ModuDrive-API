package com.moduDrive.file.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class File {

    private final UUID id;
    private final UUID namespaceId;
    private final String name;
    private final String path;
    private final UUID ownerId;
    private UUID currentVersionId;
    private Long fileSize;
    private FileStatus status;
    private final boolean directory;

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
                isDirectory.value()
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
                true
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
                isDirectory.value()
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

    public record FileId(UUID value) {}
    public record FileNamespaceId(UUID value) {}
    public record FileName(String value) {}
    public record FilePath(String value) {}
    public record FileOwnerId(UUID value) {}
    public record FileCurrentVersionId(UUID value) {}
    public record FileSize(Long value) {}
    public record FileIsDirectory(boolean value) {}
}
