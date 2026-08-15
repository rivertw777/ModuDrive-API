package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.file.domain.model.Role;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** The directory of assignable roles. {@link Role} names are the reference data; this table is
 * what {@code file_share.granted_role_id} and {@code file_role_permission.file_role_id} point at. */
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "file_role", uniqueConstraints = {
        @UniqueConstraint(name = "uk_file_role_role_name", columnNames = "role_name")
})
@Entity
class FileRoleJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role roleName;

    FileRoleJpaEntity(Role roleName) {
        this.roleName = roleName;
    }
}
