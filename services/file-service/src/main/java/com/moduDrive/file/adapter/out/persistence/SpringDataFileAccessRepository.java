package com.moduDrive.file.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataFileAccessRepository extends JpaRepository<FileAccessJpaEntity, UUID> {

    Optional<FileAccessJpaEntity> findByUserIdAndFileId(UUID userId, UUID fileId);

    List<FileAccessJpaEntity> findByUserIdOrderByAccessedAtDesc(UUID userId, Pageable pageable);
}
