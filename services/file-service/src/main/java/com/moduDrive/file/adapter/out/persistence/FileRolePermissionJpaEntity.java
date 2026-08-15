package com.moduDrive.file.adapter.out.persistence;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** The role → permission grant matrix, as a plain junction row (no id column, per the schema). */
@NoArgsConstructor
@Table(name = "file_role_permission")
@Entity
class FileRolePermissionJpaEntity {

    @EmbeddedId
    private FileRolePermissionId id;

    FileRolePermissionJpaEntity(UUID fileRoleId, UUID filePermissionId) {
        this.id = new FileRolePermissionId(fileRoleId, filePermissionId);
    }

    UUID getFileRoleId() {
        return id.getFileRoleId();
    }

    UUID getFilePermissionId() {
        return id.getFilePermissionId();
    }
}
