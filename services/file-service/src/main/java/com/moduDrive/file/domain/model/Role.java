package com.moduDrive.file.domain.model;

/**
 * What a role may actually do is data, resolved through the {@code file_role}/{@code
 * file_permission}/{@code file_role_permission} tables into a {@link Permission} set — the grant
 * matrix itself is a row change, not a code change. {@link Role} and {@link Permission} stay closed
 * enums on purpose: this repo still needs a fixed, reviewable vocabulary of role/permission names
 * (bound directly from request JSON, matched against a table row by name), not open-ended roles a
 * DB row alone could introduce.
 * <p>
 * There is no OWNER role: ownership is {@code file.owner_id}, checked directly rather than
 * granted through a share row, and is never transferable.
 */
public enum Role {

    VIEWER, EDITOR
}
