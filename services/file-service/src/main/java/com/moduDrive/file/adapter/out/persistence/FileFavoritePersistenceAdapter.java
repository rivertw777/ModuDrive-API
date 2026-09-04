package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.file.application.port.out.FileFavoritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
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
        // existsBy covers the common case (re-starring an already-starred file). It does NOT
        // fully cover a concurrent first-star race: JpaRepository.save only persist()s, so the
        // uk_file_favorite_user_file violation surfaces at the outer transaction's commit, past
        // this catch — the loser gets a 500 that a retry then resolves. H2 (test) has no
        // INSERT..ON CONFLICT and Postgres MERGE would fork the SQL, so this stays a documented
        // rough edge rather than a native upsert.
        if (fileFavoriteRepository.existsByUserIdAndFileId(userId, fileId)) {
            return;
        }
        try {
            fileFavoriteRepository.save(new FileFavoriteJpaEntity(userId, fileId));
        } catch (DataIntegrityViolationException e) {
            // Only reached if the flush happens inside this call (it usually doesn't) — harmless.
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
        // LinkedHashSet so the most-recently-starred-first order from the query survives — the
        // favorites list relies on it; the membership-check callers don't care.
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
}
