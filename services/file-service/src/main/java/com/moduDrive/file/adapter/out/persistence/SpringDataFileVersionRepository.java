package com.moduDrive.file.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataFileVersionRepository extends JpaRepository<FileVersionJpaEntity, UUID> {

    List<FileVersionJpaEntity> findByFileIdOrderByCreatedAtDesc(UUID fileId, Pageable pageable);
}
