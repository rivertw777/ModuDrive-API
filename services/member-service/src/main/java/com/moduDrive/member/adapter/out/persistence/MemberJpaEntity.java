package com.moduDrive.member.adapter.out.persistence;

import com.moduDrive.common.infrastructure.jpa.audit.BaseTimeEntity;
import com.moduDrive.member.domain.model.Role;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
// Named explicitly (matching the init SQL's own uk_member_email) so
// MemberPersistenceAdapter can tell this specific violation apart from any other
// integrity error on this table, the same way FilePersistenceAdapter does for its
// own unique constraint.
@Table(name = "member", uniqueConstraints = @UniqueConstraint(name = "uk_member_email", columnNames = "email"))
@Entity
class MemberJpaEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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