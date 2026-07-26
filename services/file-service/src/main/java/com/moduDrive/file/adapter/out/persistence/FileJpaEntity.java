package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.infrastructure.jpa.audit.BaseTimeEntity;
import com.moduDrive.file.domain.model.FileStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "file", uniqueConstraints = {
        @UniqueConstraint(name = "uk_file_namespace_path_name", columnNames = {"namespace_id", "path", "name"})
})
@Entity
class FileJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID namespaceId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private UUID ownerId;

    private UUID currentVersionId;

    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileStatus status;

    @Column(nullable = false)
    private boolean directory;

    FileJpaEntity(UUID namespaceId, String name, String path, UUID ownerId, FileStatus status, boolean directory) {
        this.namespaceId = namespaceId;
        this.name = name;
        this.path = path;
        this.ownerId = ownerId;
        this.status = status;
        this.directory = directory;
    }

    void markUploaded(UUID versionId, Long size) {
        this.status = FileStatus.UPLOADED;
        this.currentVersionId = versionId;
        this.fileSize = size;
    }

    void softDelete() {
        this.status = FileStatus.DELETED;
    }
}
