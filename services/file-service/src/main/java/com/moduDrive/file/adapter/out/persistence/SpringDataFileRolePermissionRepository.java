package com.moduDrive.file.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataFileRolePermissionRepository
        extends JpaRepository<FileRolePermissionJpaEntity, FileRolePermissionId> {
}
