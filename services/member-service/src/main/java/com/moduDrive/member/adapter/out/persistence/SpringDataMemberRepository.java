package com.moduDrive.member.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataMemberRepository extends JpaRepository<MemberJpaEntity, UUID> {
    boolean existsByEmail(String email);

    Optional<MemberJpaEntity> findByEmail(String email);
}
