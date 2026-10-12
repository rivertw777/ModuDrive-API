package com.moduDrive.file.domain.model;

/**
 * Actions a share can grant. Owner-only actions (share, revoke, delete, purge, restore, move,
 * scope) are never delegated, so they are owner checks, not permissions.
 */
public enum Permission {

    READ, DOWNLOAD, RENAME
}
