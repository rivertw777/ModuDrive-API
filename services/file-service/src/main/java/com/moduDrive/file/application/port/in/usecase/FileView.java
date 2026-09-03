package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.Role;

import java.time.LocalDateTime;

/**
 * A file plus the "it was shared with me" context the client needs to render it the same way
 * everywhere (공유 문서함 / 즐겨찾기 / 최근 문서함 / detail panel):
 * <ul>
 *   <li>{@code callerRole} — the caller's role on the file (rename needs EDITOR); null when they own it.</li>
 *   <li>{@code sharedByName}/{@code sharedByEmail} — who shared it; null when unresolved or owned.</li>
 *   <li>{@code sharedAt} — when the grant was made; null for an inherited grant or an owned file.</li>
 * </ul>
 */
public record FileView(File file, Role callerRole, String sharedByName, String sharedByEmail, LocalDateTime sharedAt) {

    /** The caller owns the file — no share context. */
    public static FileView owned(File file) {
        return new FileView(file, null, null, null, null);
    }

    /** Shared, but only the role is known (list endpoints that don't resolve the sharer per row). */
    public static FileView shared(File file, Role callerRole) {
        return new FileView(file, callerRole, null, null, null);
    }
}
