package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.file.application.port.out.FileFavoritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
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
        // existsBy covers re-starring. Two concurrent first stars still race: save only persists,
        // so the loser's uk_file_favorite_user_file violation surfaces at commit, past this catch,
        // as a 500 that a retry resolves.
        if (fileFavoriteRepository.existsByUserIdAndFileId(userId, fileId)) {
            return;
        }
        try {
            fileFavoriteRepository.save(new FileFavoriteJpaEntity(userId, fileId));
        } catch (DataIntegrityViolationException e) {
            // Only reached if the flush happens inside this call; already starred.
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
        return fileFavoriteRepository.findByUserIdOrderByRecency(userId).stream()
                .map(FileFavoriteJpaEntity::getFileId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Set<UUID> favoriteFileIdsAmong(UUID userId, Collection<UUID> fileIds) {
        if (fileIds.isEmpty()) {
            return Set.of();
        }
        return fileFavoriteRepository.findFileIdsByUserIdAndFileIdIn(userId, fileIds);
    }

    @Override
    public List<FavoriteEntry> favoritesByRecency(UUID userId) {
        return fileFavoriteRepository.findByUserIdOrderByRecency(userId).stream()
                .map(f -> new FavoriteEntry(f.getFileId(), f.getCreatedAt()))
                .toList();
    }
}
