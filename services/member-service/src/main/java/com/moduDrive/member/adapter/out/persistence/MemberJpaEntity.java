package com.moduDrive.member.adapter.out.persistence;

import com.moduDrive.common.infrastructure.jpa.audit.BaseTimeEntity;
import com.moduDrive.member.domain.model.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
// Named to match V1__init.sql: MemberPersistenceAdapter detects a duplicate email by this name.
@Table(name = "member", uniqueConstraints = @UniqueConstraint(name = "uk_member_email", columnNames = "email"))
@Entity
class MemberJpaEntity extends BaseTimeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    private String name;

    private String email;

    private String password;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "member_role", joinColumns = @JoinColumn(name = "member_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private List<Role> roles;

    private boolean isValid;

    public MemberJpaEntity(String name,
                           String email,
                           String password,
                           List<Role> roles,
                           boolean isValid) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.roles = roles;
        this.isValid = isValid;
    }

}