package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.file.domain.model.FileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataFileRepository extends JpaRepository<FileJpaEntity, UUID>, JpaSpecificationExecutor<FileJpaEntity> {

    Optional<FileJpaEntity> findByLinkToken(UUID linkToken);

    List<FileJpaEntity> findByNamespaceIdAndPathAndStatusNot(
            UUID namespaceId, String path, FileStatus status);

    Optional<FileJpaEntity> findByNamespaceIdAndPathAndNameAndStatusNot(
            UUID namespaceId, String path, String name, FileStatus status);

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

    // Trash view: trashed but not yet purged (a tombstone stays status=DELETED with deleted_at set).
    List<FileJpaEntity> findByNamespaceIdAndStatusAndDeletedAtIsNull(UUID namespaceId, FileStatus status);

    // Retention sweep: in-trash long enough, not already purged.
    List<FileJpaEntity> findByStatusAndDeletedAtIsNullAndTrashedAtBefore(FileStatus status, LocalDateTime cutoff);

    // Tombstone stamp — BaseTimeEntity's deletedAt/isDeleted, but via a plain UPDATE so no
    // @LastModifiedDate bump (see FilePersistenceAdapter.purgeFile). flush first so the
    // version/share/favorite deletes in the same purgeFile call are committed; clear after so a
    // stale managed FileJpaEntity isn't read back with the old value.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update FileJpaEntity f set f.deletedAt = :now, f.isDeleted = true "
            + "where f.id = :id and f.deletedAt is null and f.status = 'DELETED'")
    void markPurged(@Param("id") UUID id, @Param("now") LocalDateTime now);

    List<FileJpaEntity> findByNamespaceIdAndNameContainingIgnoreCaseAndStatusNot(
            UUID namespaceId, String name, FileStatus status);

    List<FileJpaEntity> findByNamespaceIdAndDirectoryFalseAndStatusNot(
            UUID namespaceId, FileStatus status);

    // Trashed (DELETED) files still occupy storage until purged, so they count here too — but a
    // purged tombstone (deleted_at set) no longer has blocks. PENDING (upload not finished, no
    // committed size) is excluded too.
    @Query("select coalesce(sum(f.fileSize), 0) from FileJpaEntity f " +
            "where f.namespaceId = :namespaceId and f.directory = false " +
            "and f.status <> 'PENDING' and f.deletedAt is null")
    long sumFileSizeByNamespaceId(@Param("namespaceId") UUID namespaceId);
}
