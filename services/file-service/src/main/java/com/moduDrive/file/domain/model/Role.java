package com.moduDrive.file.domain.model;

/**
 * Declaration order is the inclusion order: {@code EDITOR} ⊇ {@code VIEWER}.
 * ponytail: ordinal comparison only holds while roles stay fully nested — a non-nested role
 * (e.g. COMMENTER) breaks it, and that's when to switch to a role→capability set mapping.
 */
public enum Role {

    VIEWER, EDITOR;

    public boolean satisfies(Role required) {
        return this.ordinal() >= required.ordinal();
    }
}
