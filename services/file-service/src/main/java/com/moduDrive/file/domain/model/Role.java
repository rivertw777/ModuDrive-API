package com.moduDrive.file.domain.model;

import java.util.Set;

/**
 * A closed, fixed vocabulary of role/permission names, matched directly against a role name bound
 * from request JSON — not an open-ended set a DB row alone could introduce. The grant matrix below
 * is the reference data itself: it only ever changes with a code change, so it lives here rather
 * than behind a lookup.
 * <p>
 * There is no OWNER role: ownership is {@code file.owner_id}, checked directly rather than
 * granted through a share row, and is never transferable.
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
