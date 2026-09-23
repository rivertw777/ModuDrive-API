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
    /** Only meaningful while {@code accessScope == LINK}; null otherwise. */
    private Role linkRole;
    /** When this file was sent to trash; null while it is not in the trash. Set alongside
     * {@code status = TRASHED}, cleared on restore. */
    private LocalDateTime trashedAt;
    /** When this file was purged from the trash. Set exactly once, alongside the
     * {@code TRASHED -> DELETED} transition — never un-set. Non-null makes the row a tombstone:
     * its blocks/versions/shares/favorites are gone but the metadata row is kept as a deletion
     * record. Every {@link #isRemoved()} read already hides both this and a merely-trashed row. */
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

    /** Sends this file to trash — recoverable via {@link #restore()} until it's purged.
     * {@code trashedAt} is passed in, not read from the clock here, so a directory and every
     * descendant trashed in the same cascade share one instant — {@code DirectoryCascader.purge}
     * tells a cascade sibling from a later, unrelated file at a reused path by that equality. */
    public void softDelete(LocalDateTime trashedAt) {
        this.status = FileStatus.TRASHED;
        this.trashedAt = trashedAt;
    }

    public void restore() {
        this.status = FileStatus.UPLOADED;
        this.trashedAt = null;
    }

    /** True once this entry has left the live tree — trashed or purged. Every "show me what's
     * actually there" read filters this out (see {@link FileStatus#REMOVED}). */
    public boolean isRemoved() {
        return FileStatus.REMOVED.contains(status);
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

    /** A link is a bearer credential anyone who obtains it can use, unlike a named RESTRICTED
     * grant — so it only ever hands out {@link Role#VIEWER} (spec 2). The role isn't a parameter
     * precisely so no caller can express anything else: an editor link is not a state this model
     * can reach (#318). Grants no bearer secret of its own either: {@code fileId} is already the
     * capability for "anyone with the link" (see {@code FileAccessGuard.linkRoleFallback} /
     * {@code PublicFileResolver}, issue #303), so this address never changes across toggling link
     * sharing off/on. */
    public void enableLinkSharing() {
        this.accessScope = ShareScope.LINK;
        this.linkRole = Role.VIEWER;
    }

    public void disableLinkSharing() {
        this.accessScope = ShareScope.RESTRICTED;
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

        /** "report.pdf" + 1 -> "report (1).pdf" — the "keep both" name on a conflict. A directory
         * has no extension ("v1.2" -> "v1.2 (1)"), and neither does a dotfile (".env" -> ".env (1)"),
         * so for both the number goes last. */
        public FileName numbered(int n, boolean directory) {
            int dot = value.lastIndexOf('.');
            return !directory && dot > 0
                    ? new FileName(value.substring(0, dot) + " (" + n + ")" + value.substring(dot))
                    : new FileName(value + " (" + n + ")");
        }
    }
    public record FilePath(String value) {}
    public record FileOwnerId(UUID value) {}
    public record FileCurrentVersionId(UUID value) {}
    public record FileSize(Long value) {}
    public record FileIsDirectory(boolean value) {}
}
