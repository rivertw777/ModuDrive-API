package com.moduDrive.file.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

interface SpringDataFileFavoriteRepository extends JpaRepository<FileFavoriteJpaEntity, UUID> {

    boolean existsByUserIdAndFileId(UUID userId, UUID fileId);

    void deleteByUserIdAndFileId(UUID userId, UUID fileId);

    // Everyone's stars on a permanently-deleted file — no FK cascade backs this.
    void deleteByFileId(UUID fileId);

    // Most-recently-starred first. `id desc` is the tie-breaker that also orders rows predating
    // the createdAt column (null, sorted last): the PK is a time-ordered UUIDv7, so it stands in
    // for the missing timestamp and keeps the order deterministic on every dialect.
    @Query("select f from FileFavoriteJpaEntity f where f.userId = :userId "
            + "order by f.createdAt desc nulls last, f.id desc")
    List<FileFavoriteJpaEntity> findByUserIdOrderByRecency(@Param("userId") UUID userId);

    @Query("select f.fileId from FileFavoriteJpaEntity f "
            + "where f.userId = :userId and f.fileId in :fileIds")
    Set<UUID> findFileIdsByUserIdAndFileIdIn(@Param("userId") UUID userId, @Param("fileIds") Collection<UUID> fileIds);
}
