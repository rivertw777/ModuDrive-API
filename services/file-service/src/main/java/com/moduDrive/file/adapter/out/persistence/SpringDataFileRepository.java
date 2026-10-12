package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.file.domain.model.FileStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataFileRepository extends JpaRepository<FileJpaEntity, UUID>, JpaSpecificationExecutor<FileJpaEntity> {

    List<FileJpaEntity> findByNamespaceIdAndPathAndStatusNotIn(
            UUID namespaceId, String path, Collection<FileStatus> statuses);

    Optional<FileJpaEntity> findByNamespaceIdAndPathAndNameAndStatusNotIn(
            UUID namespaceId, String path, String name, Collection<FileStatus> statuses);

    // FOR UPDATE: Postgres re-checks the status after waiting on a concurrent trash, so a row trashed
    // meanwhile comes back empty.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FileJpaEntity f where f.namespaceId = :namespaceId and f.path = :path "
            + "and f.name = :name and f.status not in :statuses")
    Optional<FileJpaEntity> lockByNamespaceIdAndPathAndNameAndStatusNotIn(
            @Param("namespaceId") UUID namespaceId, @Param("path") String path, @Param("name") String name,
            @Param("statuses") Collection<FileStatus> statuses);

    // Not named "StartingWith": without this @Query, Spring Data would derive an unescaped
    // `like 'prefix%'`, and "/foo" would match "/foo2". :escapedPrefix has LIKE metacharacters
    // escaped by the caller; :prefix stays raw for the exact match. `||`, not concat(): concat() is
    // stable, so the pattern wouldn't be a plan-time constant and the prefix index (V11) would go
    // unused in every subtree query below.
    @Query("select f from FileJpaEntity f where f.namespaceId = :namespaceId " +
            "and (f.path = :prefix or f.path like (:escapedPrefix || '/%') escape '\\')")
    List<FileJpaEntity> findSubtreeByNamespaceIdAndPathPrefix(
            @Param("namespaceId") UUID namespaceId,
            @Param("prefix") String prefix,
            @Param("escapedPrefix") String escapedPrefix);

    // The same subtree, locked top-down (path, then name) — the order a commit locks the folders
    // above the file it creates, so the two never wait on each other in a cycle. Ids only: the bulk
    // UPDATEs below rewrite the rows in place, nothing needs them loaded.
    @Query(value = "select id from file where namespace_id = :namespaceId "
            + "and (path = :prefix or path like (:escapedPrefix || '/%') escape '\\') order by path, name for update",
            nativeQuery = true)
    List<UUID> lockSubtreeIds(@Param("namespaceId") UUID namespaceId, @Param("prefix") String prefix,
                              @Param("escapedPrefix") String escapedPrefix);

    // Subtree rewrites, one statement each instead of one UPDATE per row. Each mirrors the File
    // domain method it replaces (move / softDelete / restore) plus what FileJpaEntity derives from it
    // (active_slot_name) and the auditing a save would have stamped (updated_at/updated_by).
    // char_length, not Java's length(): both count characters, so a name with a non-BMP character
    // (emoji) cuts at the same place.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update file set path = concat(:newPrefix, substr(path, char_length(:prefix) + 1)), "
            + "updated_at = :now, updated_by = :by where namespace_id = :namespaceId "
            + "and (path = :prefix or path like (:escapedPrefix || '/%') escape '\\')",
            nativeQuery = true)
    int moveSubtree(@Param("namespaceId") UUID namespaceId, @Param("prefix") String prefix,
                    @Param("escapedPrefix") String escapedPrefix, @Param("newPrefix") String newPrefix,
                    @Param("now") LocalDateTime now, @Param("by") UUID by);

    // A row already trashed (individually, earlier) or purged keeps its trashed_at — see
    // DirectoryCascader.purge for why that matters.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update file set status = 'TRASHED', trashed_at = :trashedAt, active_slot_name = null, "
            + "updated_at = :now, updated_by = :by where namespace_id = :namespaceId "
            + "and (path = :prefix or path like (:escapedPrefix || '/%') escape '\\') "
            + "and status not in ('TRASHED', 'DELETED')",
            nativeQuery = true)
    int trashSubtree(@Param("namespaceId") UUID namespaceId, @Param("prefix") String prefix,
                     @Param("escapedPrefix") String escapedPrefix, @Param("trashedAt") LocalDateTime trashedAt,
                     @Param("now") LocalDateTime now, @Param("by") UUID by);

    // Only still-trashed rows: a purged one is a tombstone whose content is gone.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update file set status = 'UPLOADED', trashed_at = null, active_slot_name = name, "
            + "updated_at = :now, updated_by = :by where namespace_id = :namespaceId "
            + "and (path = :prefix or path like (:escapedPrefix || '/%') escape '\\') and status = 'TRASHED'",
            nativeQuery = true)
    int restoreSubtree(@Param("namespaceId") UUID namespaceId, @Param("prefix") String prefix,
                       @Param("escapedPrefix") String escapedPrefix, @Param("now") LocalDateTime now,
                       @Param("by") UUID by);

    // Only the subtree's TRASHED rows: a purge locks these and then releases blocks, while a commit
    // locks blocks and then active rows — locking a live folder reusing the path would close a cycle.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FileJpaEntity f where f.namespaceId = :namespaceId and f.status = 'TRASHED' " +
            "and (f.path = :prefix or f.path like (:escapedPrefix || '/%') escape '\\') order by f.path, f.name")
    List<FileJpaEntity> lockTrashedSubtreeByNamespaceIdAndPathPrefix(
            @Param("namespaceId") UUID namespaceId,
            @Param("prefix") String prefix,
            @Param("escapedPrefix") String escapedPrefix);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FileJpaEntity f where f.id = :id")
    Optional<FileJpaEntity> lockById(@Param("id") UUID id);

    List<FileJpaEntity> findByNamespaceIdAndStatus(UUID namespaceId, FileStatus status);

    List<FileJpaEntity> findByStatusAndTrashedAtBefore(FileStatus status, LocalDateTime cutoff);

    // Of these, the rows a purge may still tombstone — locked, so they stay TRASHED until it commits.
    @Query(value = "select id from file where id = any(:ids) and status = 'TRASHED' for update", nativeQuery = true)
    List<UUID> lockTrashedIds(@Param("ids") UUID[] ids);

    // Tombstone stamp: BaseTimeEntity's deletedAt/isDeleted via a plain UPDATE, so no
    // @LastModifiedDate bump. Flushed before and cleared after, so no stale entity is written or
    // read back. `status = 'TRASHED'` also makes a second purge a no-op.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    // An array, not an IN list: a folder of 100k files would be 100k bind parameters (Postgres caps at 65,535).
    @Query(value = "update file set deleted_at = :now, is_deleted = true, status = 'DELETED', deleted_by = :deletedBy "
            + "where id = any(:ids) and status = 'TRASHED'", nativeQuery = true)
    int markPurged(@Param("ids") UUID[] ids, @Param("now") LocalDateTime now, @Param("deletedBy") UUID deletedBy);

    // Active directories at any of these (path, name) slots — the caller keeps the exact pairs. The
    // slot column is name for a live row and NULL once trashed, so matching on it both drops removed
    // rows and walks uk_file_namespace_path_active_name all the way down.
    @Query("select f from FileJpaEntity f where f.namespaceId = :namespaceId and f.directory = true "
            + "and f.path in :paths and f.activeSlotName in :names")
    List<FileJpaEntity> findActiveDirectoriesAt(@Param("namespaceId") UUID namespaceId,
                                                @Param("paths") Collection<String> paths,
                                                @Param("names") Collection<String> names);

    List<FileJpaEntity> findByNamespaceIdAndNameContainingIgnoreCaseAndStatusNotIn(
            UUID namespaceId, String name, Collection<FileStatus> statuses);

    List<FileJpaEntity> findByNamespaceIdAndDirectoryFalseAndStatusNotIn(
            UUID namespaceId, Collection<FileStatus> statuses);
}
