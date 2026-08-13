package com.moduDrive.file.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataFileShareRepository extends JpaRepository<FileShareJpaEntity, UUID> {

    boolean existsByFileIdAndSharedWithUserId(UUID fileId, UUID sharedWithUserId);

    Optional<FileShareJpaEntity> findByFileIdAndSharedWithUserId(UUID fileId, UUID sharedWithUserId);

    List<FileShareJpaEntity> findByFileId(UUID fileId);

    List<FileShareJpaEntity> findBySharedWithUserId(UUID sharedWithUserId);
}
