package com.moduDrive.file.application.port.out;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Per-user favorites: one row per (user, file), for every user including the file's owner. This
 * is the single source of truth for whether a file is starred — the "즐겨찾기" list reads and
 * orders by it, and every other list/detail response fills its {@code favorite} flag from here
 * for the actual caller.
 */
public interface FileFavoritePort {

    void favorite(UUID userId, UUID fileId);

    void unfavorite(UUID userId, UUID fileId);

    boolean isFavorite(UUID userId, UUID fileId);

    /** File ids the user has starred, most-recently-starred first (a {@link LinkedHashSet}) —
     * for the favorites list, which needs the whole set in order. */
    Set<UUID> favoriteFileIds(UUID userId);

    /** Which of {@code fileIds} the user has starred — one query to fill the {@code favorite}
     * flag for a whole list page instead of a check per row. Empty when {@code fileIds} is empty. */
    Set<UUID> favoriteFileIdsAmong(UUID userId, Collection<UUID> fileIds);

    /** Same rows/order as {@link #favoriteFileIds}, but carrying when each was starred — the
     * favorites list surfaces this as "즐겨찾기한 날짜". */
    List<FavoriteEntry> favoritesByRecency(UUID userId);

    record FavoriteEntry(UUID fileId, LocalDateTime favoritedAt) {}
}
