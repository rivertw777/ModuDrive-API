package com.moduDrive.file.domain.model;

/**
 * The vocabulary of shareable actions on a file. Owner-only actions (share, revoke, delete,
 * purge, restore, move, favorite, scope toggle) are deliberately absent — they are never
 * delegated to a grantee, so they stay behind an owner check rather than becoming permissions.
 */
public enum Permission {

    READ, DOWNLOAD, RENAME
}
