package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.Role;

import java.time.LocalDateTime;

/**
 * A file plus the per-list-context metadata the client needs to render it the same way
 * everywhere (공유 문서함 / 즐겨찾기 / 최근 문서함 / detail panel):
 * <ul>
 *   <li>{@code callerRole} — the caller's role on the file (rename needs EDITOR); null when they own it.</li>
 *   <li>{@code sharedByName}/{@code sharedByEmail} — who shared it; null when unresolved or owned.</li>
 *   <li>{@code sharedAt} — when the grant was made; null for an inherited grant or an owned file.</li>
 *   <li>{@code accessedAt} — when the caller last opened it; only set by 최근 문서함.</li>
 *   <li>{@code favoritedAt} — when the caller starred it; only set by 즐겨찾기.</li>
 * </ul>
 */
public record FileView(
        File file,
        Role callerRole,
        String sharedByName,
        String sharedByEmail,
        LocalDateTime sharedAt,
        LocalDateTime accessedAt,
        LocalDateTime favoritedAt
) {

    /** The caller owns the file — no share context. */
    public static FileView owned(File file) {
        return new FileView(file, null, null, null, null, null, null);
    }

    /** Shared, but only the role is known (list endpoints that don't resolve the sharer per row). */
    public static FileView shared(File file, Role callerRole) {
        return new FileView(file, callerRole, null, null, null, null, null);
    }

    /** 최근 문서함: attaches when the caller opened this file. */
    public FileView withAccessedAt(LocalDateTime accessedAt) {
        return new FileView(file, callerRole, sharedByName, sharedByEmail, sharedAt, accessedAt, favoritedAt);
    }

    /** 즐겨찾기: attaches when the caller starred this file. */
    public FileView withFavoritedAt(LocalDateTime favoritedAt) {
        return new FileView(file, callerRole, sharedByName, sharedByEmail, sharedAt, accessedAt, favoritedAt);
    }
}
