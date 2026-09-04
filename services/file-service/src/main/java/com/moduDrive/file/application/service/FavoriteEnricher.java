package com.moduDrive.file.application.service;

import com.moduDrive.file.application.port.out.FileFavoritePort;
import com.moduDrive.file.domain.model.File;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Fills the transient {@code favorite} flag on a list of files for one caller. Favorites live
 * only in {@code file_favorite} now (see {@code FileFavoritePort}), so every owner-facing listing
 * that shows the star has to resolve it here — one batched query per page, not a lookup per row.
 */
@Component
@RequiredArgsConstructor
class FavoriteEnricher {

    private final FileFavoritePort fileFavoritePort;

    List<File> withFavorites(UUID userId, List<File> files) {
        if (files.isEmpty()) {
            return files;
        }
        Set<UUID> starred = fileFavoritePort.favoriteFileIdsAmong(
                userId, files.stream().map(File::getId).toList());
        files.forEach(file -> file.markFavorite(starred.contains(file.getId())));
        return files;
    }

    /** Single file — for the echo a mutation (rename/move/restore) returns, whose favorite state
     * is unchanged by the operation but still has to be on the response. */
    File withFavorite(UUID userId, File file) {
        file.markFavorite(fileFavoritePort.isFavorite(userId, file.getId()));
        return file;
    }
}
