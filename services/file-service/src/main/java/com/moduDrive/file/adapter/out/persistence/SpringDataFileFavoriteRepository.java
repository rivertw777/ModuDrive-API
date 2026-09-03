package com.moduDrive.file.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataFileFavoriteRepository extends JpaRepository<FileFavoriteJpaEntity, UUID> {

    boolean existsByUserIdAndFileId(UUID userId, UUID fileId);

    void deleteByUserIdAndFileId(UUID userId, UUID fileId);

    List<FileFavoriteJpaEntity> findByUserId(UUID userId);
}
