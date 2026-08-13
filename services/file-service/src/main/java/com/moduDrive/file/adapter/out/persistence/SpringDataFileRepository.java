package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.file.domain.model.FileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataFileRepository extends JpaRepository<FileJpaEntity, UUID> {

    Optional<FileJpaEntity> findByLinkToken(UUID linkToken);

    List<FileJpaEntity> findByNamespaceIdAndPathAndStatusNot(
            UUID namespaceId, String path, FileStatus status);

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

    List<FileJpaEntity> findByNamespaceIdAndStatus(UUID namespaceId, FileStatus status);

    List<FileJpaEntity> findByNamespaceIdAndFavoriteTrueAndStatusNot(UUID namespaceId, FileStatus status);

    List<FileJpaEntity> findByNamespaceIdAndNameContainingIgnoreCaseAndStatusNot(
            UUID namespaceId, String name, FileStatus status);

    List<FileJpaEntity> findByNamespaceIdAndDirectoryFalseAndStatusNot(
            UUID namespaceId, FileStatus status);

    // Trashed (DELETED) files still occupy storage until purged, so they count here too —
    // only PENDING (upload not yet finished, no committed size) is excluded.
    @Query("select coalesce(sum(f.fileSize), 0) from FileJpaEntity f " +
            "where f.namespaceId = :namespaceId and f.directory = false and f.status <> 'PENDING'")
    long sumFileSizeByNamespaceId(@Param("namespaceId") UUID namespaceId);
}
