package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.file.domain.model.FileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface SpringDataFileRepository extends JpaRepository<FileJpaEntity, UUID> {

    List<FileJpaEntity> findByNamespaceIdAndPathAndStatusNot(
            UUID namespaceId, String path, FileStatus status);

    List<FileJpaEntity> findByNamespaceIdAndStatus(UUID namespaceId, FileStatus status);

    List<FileJpaEntity> findByNamespaceIdAndFavoriteTrueAndStatusNot(UUID namespaceId, FileStatus status);

    List<FileJpaEntity> findByNamespaceIdAndNameContainingIgnoreCaseAndStatusNot(
            UUID namespaceId, String name, FileStatus status);

    List<FileJpaEntity> findByNamespaceIdAndDirectoryFalseAndStatusNot(
            UUID namespaceId, FileStatus status);

    @Query("select coalesce(sum(f.fileSize), 0) from FileJpaEntity f " +
            "where f.namespaceId = :namespaceId and f.directory = false and f.status = 'UPLOADED'")
    long sumFileSizeByNamespaceId(@Param("namespaceId") UUID namespaceId);
}
