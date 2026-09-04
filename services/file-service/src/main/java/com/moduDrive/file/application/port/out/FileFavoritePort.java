package com.moduDrive.file.application.port.out;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-user favorites for files the user does not own. The owner's own favorites stay on
 * {@code file.favorite}; a shared VIEWER/EDITOR can't touch that column, so their stars live here
 * instead, one row per (user, file).
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
