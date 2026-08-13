package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.infrastructure.jpa.audit.BaseTimeEntity;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.ShareScope;
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

    @Column(nullable = false)
    private boolean favorite;

    // Left DB-nullable on purpose: ddl-auto=update can't add a NOT NULL column to a table that
    // already has rows, so pre-existing files would break the migration. FileMapper reads a null
    // back as RESTRICTED (the safe default).
    @Enumerated(EnumType.STRING)
    private ShareScope accessScope;

    @Column(unique = true)
    private UUID linkToken;

    FileJpaEntity(UUID namespaceId, String name, String path, UUID ownerId, FileStatus status, boolean directory) {
        this.namespaceId = namespaceId;
        this.name = name;
        this.path = path;
        this.ownerId = ownerId;
        this.status = status;
        this.directory = directory;
        this.accessScope = ShareScope.RESTRICTED;
    }

    void applyChanges(String name, String path, UUID currentVersionId, Long fileSize, FileStatus status,
                      boolean favorite, ShareScope accessScope, UUID linkToken) {
        this.name = name;
        this.path = path;
        this.currentVersionId = currentVersionId;
        this.fileSize = fileSize;
        this.status = status;
        this.favorite = favorite;
        this.accessScope = accessScope;
        this.linkToken = linkToken;
    }
}
