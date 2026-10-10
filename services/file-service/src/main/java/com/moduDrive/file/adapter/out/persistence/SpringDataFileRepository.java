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

    // Named to avoid Spring Data's "StartingWith" derived-query keyword — losing the @Query here
    // would silently fall back to an unescaped `like 'prefix%'` and reintroduce the prefix-collision
    // bug (e.g. "/foo" matching "/foo2") this hand-written JPQL exists to prevent.
    // :escapedPrefix has LIKE metacharacters (%, _, \) escaped by the caller; :prefix stays raw for
    // the exact-match branch.
    @Query("select f from FileJpaEntity f where f.namespaceId = :namespaceId " +
            "and (f.path = :prefix or f.path like concat(:escapedPrefix, '/%') escape '\\')")
    List<FileJpaEntity> findSubtreeByNamespaceIdAndPathPrefix(
            @Param("namespaceId") UUID namespaceId,
            @Param("prefix") String prefix,
            @Param("escapedPrefix") String escapedPrefix);

    // The same subtree, locked top-down (path, then name) — the order a commit locks the folders
    // above the file it creates, so the two never wait on each other in a cycle.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FileJpaEntity f where f.namespaceId = :namespaceId " +
            "and (f.path = :prefix or f.path like concat(:escapedPrefix, '/%') escape '\\') order by f.path, f.name")
    List<FileJpaEntity> lockSubtreeByNamespaceIdAndPathPrefix(
            @Param("namespaceId") UUID namespaceId,
            @Param("prefix") String prefix,
            @Param("escapedPrefix") String escapedPrefix);

    // Only the subtree's TRASHED rows: a purge locks these and then releases blocks, while a commit
    // locks blocks and then active rows — locking a live folder reusing the path would close a cycle.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FileJpaEntity f where f.namespaceId = :namespaceId and f.status = 'TRASHED' " +
            "and (f.path = :prefix or f.path like concat(:escapedPrefix, '/%') escape '\\') order by f.path, f.name")
    List<FileJpaEntity> lockTrashedSubtreeByNamespaceIdAndPathPrefix(
            @Param("namespaceId") UUID namespaceId,
            @Param("prefix") String prefix,
            @Param("escapedPrefix") String escapedPrefix);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FileJpaEntity f where f.id = :id")
    Optional<FileJpaEntity> lockById(@Param("id") UUID id);

    // Trash view: status alone is enough now — TRASHED never has deletedAt set (purge is the only
    // thing that sets it, and purge moves status to DELETED in the same update).
    List<FileJpaEntity> findByNamespaceIdAndStatus(UUID namespaceId, FileStatus status);

    // Retention sweep: in-trash long enough. Same "TRASHED never has deletedAt" invariant as above.
    List<FileJpaEntity> findByStatusAndTrashedAtBefore(FileStatus status, LocalDateTime cutoff);

    // Tombstone stamp — BaseTimeEntity's deletedAt/isDeleted, but via a plain UPDATE so no
    // @LastModifiedDate bump (see FilePersistenceAdapter.purgeFile). flush first so the
    // version/share/favorite deletes in the same purgeFile call are committed; clear after so a
    // stale managed FileJpaEntity isn't read back with the old value. The WHERE clause is the
    // purge precondition (only a currently-trashed row is purge-eligible) and doubles as the
    // idempotency guard — a second concurrent call no longer matches once the first commits.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update FileJpaEntity f set f.deletedAt = :now, f.isDeleted = true, f.status = 'DELETED', "
            + "f.deletedBy = :deletedBy where f.id = :id and f.status = 'TRASHED'")
    int markPurged(@Param("id") UUID id, @Param("now") LocalDateTime now, @Param("deletedBy") UUID deletedBy);

    List<FileJpaEntity> findByNamespaceIdAndNameContainingIgnoreCaseAndStatusNotIn(
            UUID namespaceId, String name, Collection<FileStatus> statuses);

    List<FileJpaEntity> findByNamespaceIdAndDirectoryFalseAndStatusNotIn(
            UUID namespaceId, Collection<FileStatus> statuses);

    // Trashed files still occupy storage until purged, so they count here too — but a purged
    // tombstone (deleted_at set) no longer has blocks. This is independent of the TRASHED/DELETED
    // status split above: deletedAt alone already tells "still has blocks" apart from "purged".
    // PENDING (upload not finished, no committed size) is excluded too.
    @Query("select coalesce(sum(f.fileSize), 0) from FileJpaEntity f " +
            "where f.namespaceId = :namespaceId and f.directory = false " +
            "and f.status <> 'PENDING' and f.deletedAt is null")
    long sumFileSizeByNamespaceId(@Param("namespaceId") UUID namespaceId);
}
