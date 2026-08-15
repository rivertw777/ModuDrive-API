package com.moduDrive.file.adapter.out.persistence;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/** Composite key of {@code file_role_permission}: the table has no id column of its own, the
 * (role, permission) pair is the identity. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
class FileRolePermissionId implements Serializable {

    private UUID fileRoleId;
    private UUID filePermissionId;
}
