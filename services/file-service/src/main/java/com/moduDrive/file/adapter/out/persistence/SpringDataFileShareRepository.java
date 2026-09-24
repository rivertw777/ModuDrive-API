package com.moduDrive.file.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataFileShareRepository extends JpaRepository<FileShareJpaEntity, UUID> {

    /** Bulk JPQL delete instead of {@link #deleteById}: concurrent revokes of the same grantee's
     * ancestor shares (see RevokeFileShareService#revokeAncestorGrants) can race to delete the
     * same row, and the entity-based deleteById throws ObjectOptimisticLockingFailureException
     * when 0 rows remain by flush time. A bulk delete is a no-op instead. */
    @Modifying
    @Query("delete from FileShareJpaEntity f where f.id = :id")
    void deleteByIdBulk(@Param("id") UUID id);

    boolean existsByFileIdAndSharedWithUserId(UUID fileId, UUID sharedWithUserId);

    boolean existsByFileIdAndGranteeEmail(UUID fileId, String granteeEmail);

    Optional<FileShareJpaEntity> findByFileIdAndSharedWithUserId(UUID fileId, UUID sharedWithUserId);

    Optional<FileShareJpaEntity> findByFileIdAndGranteeEmail(UUID fileId, String granteeEmail);

    Optional<FileShareJpaEntity> findByToken(UUID token);

    List<FileShareJpaEntity> findByFileId(UUID fileId);

    boolean existsByFileIdIn(List<UUID> fileIds);

    void deleteByFileId(UUID fileId);

    // Most recently shared first — drives the "공유 문서함" list order.
    List<FileShareJpaEntity> findBySharedWithUserIdOrderByCreatedAtDesc(UUID sharedWithUserId);

    List<FileShareJpaEntity> findByGranteeEmailAndSharedWithUserIdIsNull(String granteeEmail);
}
