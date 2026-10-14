package com.moduDrive.file.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.text.Normalizer;
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
    /** Only meaningful while {@code accessScope == LINK}; null otherwise. */
    private Role linkRole;
    /** Set with {@code status = TRASHED}, cleared on restore. */
    private LocalDateTime trashedAt;
    /** Set once by a purge ({@code TRASHED -> DELETED}) and never cleared: the row is then a
     * tombstone whose blocks/versions/shares/favorites are gone. */
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
                null
        );
    }

    public void markUploaded(UUID versionId, Long size) {
        this.status = FileStatus.UPLOADED;
        this.currentVersionId = versionId;
        this.fileSize = size;
    }

    /** {@code trashedAt} is passed in so a directory and every descendant trashed in the same
     * cascade share one instant — {@code DirectoryCascader.purge} relies on that equality. */
    public void softDelete(LocalDateTime trashedAt) {
        this.status = FileStatus.TRASHED;
        this.trashedAt = trashedAt;
    }

    public void restore() {
        this.status = FileStatus.UPLOADED;
        this.trashedAt = null;
    }

    /** Trashed or purged (see {@link FileStatus#REMOVED}). */
    public boolean isRemoved() {
        return FileStatus.REMOVED.contains(status);
    }

    public void rename(FileName name) {
        this.name = name.value();
    }

    public void move(FilePath path) {
        this.path = path.value();
    }

    /** {@code path} joined with {@code name} — what child entries store as their {@code path}. */
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

    /** A link is a bearer credential, so it only ever grants {@link Role#VIEWER}; the role isn't a
     * parameter so no caller can make an editor link. There is no separate link token: the
     * {@code fileId} is the capability (see {@code FileAccessGuard.linkRoleFallback}). */
    public void enableLinkSharing() {
        this.accessScope = ShareScope.LINK;
        this.linkRole = Role.VIEWER;
    }

    public void disableLinkSharing() {
        this.accessScope = ShareScope.RESTRICTED;
        this.linkRole = null;
    }

    /** macOS hands over Hangul names decomposed (NFD), Windows composed (NFC): the same name would
     * otherwise be two rows that never conflict. Every name and path is stored and looked up in NFC. */
    public static String nfc(String value) {
        return value == null ? null : Normalizer.normalize(value, Normalizer.Form.NFC);
    }

    public record FileId(UUID value) {}
    public record FileNamespaceId(UUID value) {}
    public record FileName(String value) {
        /** Rejects a path separator, which would corrupt {@link #fullPath()}, and "." / "..",
         * which a path would read as a segment. */
        public FileName {
            if (value == null || value.isBlank()
                    || value.contains("/") || value.contains("\\")
                    || value.equals(".") || value.equals("..")) {
                throw new IllegalArgumentException("파일/디렉토리 이름에 /, \\, ., .. 는 사용할 수 없습니다.");
            }
            value = nfc(value);
        }

        /** "report.pdf" + 1 -> "report (1).pdf", the "keep both" name on a conflict. A directory
         * or a dotfile has no extension, so the number goes last ("v1.2 (1)", ".env (1)"). */
        public FileName numbered(int n, boolean directory) {
            int dot = value.lastIndexOf('.');
            return !directory && dot > 0
                    ? new FileName(value.substring(0, dot) + " (" + n + ")" + value.substring(dot))
                    : new FileName(value + " (" + n + ")");
        }
    }
    public record FilePath(String value) {
        public FilePath {
            value = nfc(value);
        }
    }
    public record FileOwnerId(UUID value) {}
    public record FileCurrentVersionId(UUID value) {}
    public record FileSize(Long value) {}
    public record FileIsDirectory(boolean value) {}
}
