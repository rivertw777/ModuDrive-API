package com.moduDrive.file.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataFileShareRepository extends JpaRepository<FileShareJpaEntity, UUID> {

    /** Bulk delete, not {@link #deleteById}: concurrent revokes can race to delete the same
     * ancestor share, and deleteById throws ObjectOptimisticLockingFailureException when the row is
     * already gone. */
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

    // Bulk, for a purge: a derived deleteByFileId loads every row and deletes them one by one.
    @Modifying
    @Query(value = "delete from file_share where file_id = any(:fileIds)", nativeQuery = true)
    void deleteByFileIds(@Param("fileIds") UUID[] fileIds);

    // The caller's grants on any of these files — a file and its ancestors in one query.
    List<FileShareJpaEntity> findByFileIdInAndSharedWithUserId(Collection<UUID> fileIds, UUID sharedWithUserId);

    // Most recently shared first — drives the "공유 문서함" list order.
    List<FileShareJpaEntity> findBySharedWithUserIdOrderByCreatedAtDesc(UUID sharedWithUserId);

    List<FileShareJpaEntity> findByGranteeEmailAndSharedWithUserIdIsNull(String granteeEmail);
}
