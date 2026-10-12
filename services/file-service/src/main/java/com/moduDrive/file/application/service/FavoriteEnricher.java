package com.moduDrive.file.application.service;

import com.moduDrive.file.application.port.out.FileFavoritePort;
import com.moduDrive.file.domain.model.File;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Fills the transient {@code favorite} flag for one caller from {@code file_favorite}, one query
 * per list.
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

    /** Single file, for the response of a rename/move/restore. */
    File withFavorite(UUID userId, File file) {
        file.markFavorite(fileFavoritePort.isFavorite(userId, file.getId()));
        return file;
    }
}
