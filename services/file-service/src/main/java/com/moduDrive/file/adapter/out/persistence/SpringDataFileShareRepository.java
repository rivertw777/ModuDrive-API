package com.moduDrive.file.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataFileShareRepository extends JpaRepository<FileShareJpaEntity, UUID> {

    boolean existsByFileIdAndSharedWithUserId(UUID fileId, UUID sharedWithUserId);
}
