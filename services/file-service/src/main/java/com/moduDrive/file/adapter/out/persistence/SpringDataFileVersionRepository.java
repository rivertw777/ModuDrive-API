package com.moduDrive.file.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataFileVersionRepository extends JpaRepository<FileVersionJpaEntity, UUID> {

    List<FileVersionJpaEntity> findByFileIdOrderByCreatedAtDesc(UUID fileId, Pageable pageable);

    Optional<FileVersionJpaEntity> findByUploadId(UUID uploadId);

    List<FileVersionJpaEntity> findByUploadIdIn(Collection<UUID> uploadIds);

    // Bulk deletes for a purge — the blocklist (an element collection) first, since a JPQL/native
    // delete of the version rows doesn't cascade to it.
    @Modifying
    @Query(value = "delete from file_version_block where version_id in "
            + "(select id from file_version where file_id = any(:fileIds))", nativeQuery = true)
    void deleteBlocklistsByFileIds(@Param("fileIds") UUID[] fileIds);

    @Modifying
    @Query(value = "delete from file_version where file_id = any(:fileIds)", nativeQuery = true)
    void deleteByFileIds(@Param("fileIds") UUID[] fileIds);

    // Files over the version cap, or with an old version besides the current one — the current is
    // always the newest (a commit makes its new version current), so with more than one version the
    // oldest isn't current. Keyset on file_id. A file listed here may still have nothing to drop
    // once locked; findVersionsToPrune decides exactly.
    @Query(value = "select v.file_id from file_version v where v.file_id > :after "
            + "group by v.file_id "
            + "having count(*) > :keep or (count(*) > 1 and min(v.created_at) < :cutoff) "
            + "order by v.file_id limit :limit",
            nativeQuery = true)
    List<UUID> findFileIdsWithVersionsToPrune(@Param("cutoff") LocalDateTime cutoff, @Param("keep") int keep,
                                              @Param("after") UUID after, @Param("limit") int limit);

    // The file's versions the retention rule drops: never the current one, otherwise any older
    // than the cutoff or outside the newest :keep. Only a live file's — a trashed one's versions all go
    // with its purge, and pruning it would lock blocks then the file against the purge's file-then-blocks.
    @Query(value = "select v.* from file_version v join file f on f.id = v.file_id "
            + "where v.file_id = :fileId and f.status = 'UPLOADED' and v.id is distinct from f.current_version_id "
            + "and (v.created_at < :cutoff or v.id not in ("
            + "select n.id from file_version n where n.file_id = :fileId order by n.created_at desc limit :keep))",
            nativeQuery = true)
    List<FileVersionJpaEntity> findVersionsToPrune(@Param("fileId") UUID fileId, @Param("cutoff") LocalDateTime cutoff,
                                                   @Param("keep") int keep);
}
