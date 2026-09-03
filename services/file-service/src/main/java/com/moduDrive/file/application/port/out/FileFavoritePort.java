package com.moduDrive.file.application.port.out;

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

    Set<UUID> favoriteFileIds(UUID userId);
}
