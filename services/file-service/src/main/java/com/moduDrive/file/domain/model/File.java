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
    /** Only meaningful while {@code accessScope == LINK}; null otherwise. */
    private Role linkRole;
    /** When this file was sent to trash; null while it is not in the trash. */
    private LocalDateTime trashedAt;
    /** When this file was purged from the trash. Non-null makes the row a tombstone — its
     * blocks/versions/shares/favorites are gone but the metadata row is kept as a deletion
     * record. Every {@code status != DELETED} read already hides it. */
    private LocalDateTime deletedAt;

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
                null,
                null,
                null,
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
                null,
                null,
                null,
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
                null,
                null,
                null,
                null
        );
    }

    /** Re-targets this active file for a fresh upload after the caller has explicitly consented
     * to replacing it (a same-name re-upload becomes a new version, not a new file).
     * {@code markUploaded} completes it once the new content lands; {@code currentVersionId}/
     * {@code fileSize} are left as-is until then so the previous version stays visible while the
     * new one is in flight. */
    public void restartUpload() {
        this.status = FileStatus.PENDING;
    }

    public void markUploaded(UUID versionId, Long size) {
        this.status = FileStatus.UPLOADED;
        this.currentVersionId = versionId;
        this.fileSize = size;
    }

    public void softDelete() {
        this.status = FileStatus.DELETED;
        this.trashedAt = LocalDateTime.now();
    }

    public void restore() {
        this.status = FileStatus.UPLOADED;
        this.trashedAt = null;
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

    public void markTrashedAt(LocalDateTime trashedAt) {
        this.trashedAt = trashedAt;
    }

    public void markDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    /** Idempotent in the token: an already-issued token is kept, so re-selecting LINK never
     * invalidates links already handed out. Rotation would need its own explicit operation.
     * {@code role} is always re-applied, which is how a viewer link is upgraded to an editor one. */
    public void enableLinkSharing(UUID token, Role role) {
        this.accessScope = ShareScope.LINK;
        if (this.linkToken == null) {
            this.linkToken = token;
        }
        this.linkRole = role;
    }

    public void disableLinkSharing() {
        this.accessScope = ShareScope.RESTRICTED;
        this.linkToken = null;
        this.linkRole = null;
    }

    public record FileId(UUID value) {}
    public record FileNamespaceId(UUID value) {}
    public record FileName(String value) {
        /** Rejects anything that would corrupt {@link #fullPath()} (a path separator embedded in
         * a name) or that {@code DirectoryCascader.movePath} would misread as a path segment
         * ("." / ".."), not just for the create-directory entry point that already had this
         * check, but for every caller — including rename, which had none (#210). */
        public FileName {
            if (value == null || value.isBlank()
                    || value.contains("/") || value.contains("\\")
                    || value.equals(".") || value.equals("..")) {
                throw new IllegalArgumentException("파일/디렉토리 이름에 /, \\, ., .. 는 사용할 수 없습니다.");
            }
        }
    }
    public record FilePath(String value) {}
    public record FileOwnerId(UUID value) {}
    public record FileCurrentVersionId(UUID value) {}
    public record FileSize(Long value) {}
    public record FileIsDirectory(boolean value) {}
}
