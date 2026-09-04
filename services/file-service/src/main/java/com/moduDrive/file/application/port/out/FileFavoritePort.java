package com.moduDrive.file.application.port.out;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-user favorites: one row per (user, file), for every user including the file's owner — this
 * is the source of truth the "즐겨찾기" list reads and orders by. The owner's row is additionally
 * mirrored onto the {@code file.favorite} flag that owner-facing listings read cheaply; a
 * non-owner's star never touches that column.
 */
public interface FileFavoritePort {

    void favorite(UUID userId, UUID fileId);

    void unfavorite(UUID userId, UUID fileId);

    boolean isFavorite(UUID userId, UUID fileId);

    /** File ids the user has starred. Iteration order is most-recently-starred first (a
     * {@link LinkedHashSet}) so the favorites list can rely on it; membership-check callers
     * can ignore the order. */
    Set<UUID> favoriteFileIds(UUID userId);
}
