package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.file.domain.model.Permission;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** The directory of grantable permissions. {@link Permission} names are the reference data; this
 * table is what {@code file_role_permission.file_permission_id} points at. */
@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "file_permission", uniqueConstraints = {
        @UniqueConstraint(name = "uk_file_permission_permission_name", columnNames = "permission_name")
})
@Entity
class FilePermissionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Permission permissionName;

    FilePermissionJpaEntity(Permission permissionName) {
        this.permissionName = permissionName;
    }
}
