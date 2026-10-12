package com.moduDrive.file.application.port.out;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Per-user favorites: one row per (user, file), owner included. Every response's
 * {@code favorite} flag comes from here, for the actual caller.
 */
public interface FileFavoritePort {

    void favorite(UUID userId, UUID fileId);

    void unfavorite(UUID userId, UUID fileId);

    boolean isFavorite(UUID userId, UUID fileId);

    /** File ids the user has starred, most recently starred first. */
    Set<UUID> favoriteFileIds(UUID userId);

    /** Which of {@code fileIds} the user has starred, in one query. */
    Set<UUID> favoriteFileIdsAmong(UUID userId, Collection<UUID> fileIds);

    /** {@link #favoriteFileIds} with when each was starred, for the favorites list. */
    List<FavoriteEntry> favoritesByRecency(UUID userId);

    record FavoriteEntry(UUID fileId, LocalDateTime favoritedAt) {}
}
