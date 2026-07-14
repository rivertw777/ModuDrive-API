package com.moduDrive.file.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataNamespaceRepository extends JpaRepository<NamespaceJpaEntity, UUID> {

    boolean existsByUserId(Long userId);

    Optional<NamespaceJpaEntity> findByUserId(Long userId);
}
