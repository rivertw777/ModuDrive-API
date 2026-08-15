package com.moduDrive.file.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataFileRoleRepository extends JpaRepository<FileRoleJpaEntity, UUID> {
}
