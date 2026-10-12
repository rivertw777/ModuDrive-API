package com.moduDrive.file.domain.model;

import java.util.Set;

/**
 * A share's role and the permissions it grants. There is no OWNER role: ownership is
 * {@code file.owner_id}, checked directly, and is never transferable.
 */
public enum Role {

    VIEWER(Set.of(Permission.READ, Permission.DOWNLOAD)),
    EDITOR(Set.of(Permission.READ, Permission.DOWNLOAD, Permission.RENAME));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> permissions() {
        return permissions;
    }
}
