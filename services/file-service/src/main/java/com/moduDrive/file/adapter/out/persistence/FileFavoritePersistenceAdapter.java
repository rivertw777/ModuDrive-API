package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.file.application.port.out.FileFavoritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@PersistenceAdapter
@RequiredArgsConstructor
class FileFavoritePersistenceAdapter implements FileFavoritePort {

    private final SpringDataFileFavoriteRepository fileFavoriteRepository;

    @Transactional
    @Override
    public void favorite(UUID userId, UUID fileId) {
        if (fileFavoriteRepository.existsByUserIdAndFileId(userId, fileId)) {
            return;
        }
        try {
            fileFavoriteRepository.save(new FileFavoriteJpaEntity(userId, fileId));
        } catch (DataIntegrityViolationException e) {
            // Concurrent double-favorite raced past existsBy — the unique constraint caught it,
            // and "already favorited" is exactly the desired end state.
        }
    }

    @Transactional
    @Override
    public void unfavorite(UUID userId, UUID fileId) {
        fileFavoriteRepository.deleteByUserIdAndFileId(userId, fileId);
    }

    @Override
    public boolean isFavorite(UUID userId, UUID fileId) {
        return fileFavoriteRepository.existsByUserIdAndFileId(userId, fileId);
    }

    @Override
    public Set<UUID> favoriteFileIds(UUID userId) {
        return fileFavoriteRepository.findByUserId(userId).stream()
                .map(FileFavoriteJpaEntity::getFileId)
                .collect(Collectors.toSet());
    }
}
