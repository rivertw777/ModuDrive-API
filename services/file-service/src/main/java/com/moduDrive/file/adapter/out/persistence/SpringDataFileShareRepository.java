package com.moduDrive.file.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataFileShareRepository extends JpaRepository<FileShareJpaEntity, UUID> {

    boolean existsByFileIdAndSharedWithUserId(UUID fileId, UUID sharedWithUserId);

    boolean existsByFileIdAndGranteeEmail(UUID fileId, String granteeEmail);

    Optional<FileShareJpaEntity> findByFileIdAndSharedWithUserId(UUID fileId, UUID sharedWithUserId);

    /** Excludes a pending guest share whose invite is older than {@code createdAfter} — see
     * {@code FilePersistenceAdapter#findByToken} (#211: this token had no expiry at all). */
    Optional<FileShareJpaEntity> findByTokenAndCreatedAtAfter(UUID token, LocalDateTime createdAfter);

    List<FileShareJpaEntity> findByFileId(UUID fileId);

    void deleteByFileId(UUID fileId);

    // Most recently shared first — drives the "공유 문서함" list order.
    List<FileShareJpaEntity> findBySharedWithUserIdOrderByCreatedAtDesc(UUID sharedWithUserId);

    List<FileShareJpaEntity> findByGranteeEmailAndSharedWithUserIdIsNull(String granteeEmail);
}
