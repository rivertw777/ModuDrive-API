package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.file.domain.model.FileStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataFileRepository extends JpaRepository<FileJpaEntity, UUID> {

    List<FileJpaEntity> findByNamespaceIdAndPathStartingWithAndStatusNot(
            UUID namespaceId, String pathPrefix, FileStatus status);
}
