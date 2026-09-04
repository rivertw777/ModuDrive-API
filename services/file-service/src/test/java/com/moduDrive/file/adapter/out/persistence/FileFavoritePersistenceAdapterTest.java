package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.infrastructure.jpa.config.AuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
// AuditingConfig is a third-party auto-configuration the @DataJpaTest slice drops — without it
// @CreatedDate never fires and the recency ordering can't be exercised.
@Import({FileFavoritePersistenceAdapter.class, AuditingConfig.class})
class FileFavoritePersistenceAdapterTest {

    @Autowired
    private FileFavoritePersistenceAdapter fileFavoritePersistenceAdapter;

    private final UUID userId = UUID.randomUUID();

    @Nested
    @DisplayName("즐겨찾기를 추가/삭제할 때")
    class WhenTogglingFavorites {

        @Test
        @DisplayName("favorite는 멱등이고, unfavorite하면 사라진다")
        void favoriteIsIdempotentAndUnfavoriteClears() {
            UUID fileId = UUID.randomUUID();

            fileFavoritePersistenceAdapter.favorite(userId, fileId);
            fileFavoritePersistenceAdapter.favorite(userId, fileId);
            assertThat(fileFavoritePersistenceAdapter.isFavorite(userId, fileId)).isTrue();

            fileFavoritePersistenceAdapter.unfavorite(userId, fileId);
            assertThat(fileFavoritePersistenceAdapter.isFavorite(userId, fileId)).isFalse();
        }
    }

    @Nested
    @DisplayName("즐겨찾기 파일 id를 조회할 때")
    class WhenListingFavoriteFileIds {

        @Test
        @DisplayName("최근 즐겨찾기한 순으로 반환한다")
        void mostRecentFirst() {
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            UUID third = UUID.randomUUID();

            fileFavoritePersistenceAdapter.favorite(userId, first);
            fileFavoritePersistenceAdapter.favorite(userId, second);
            fileFavoritePersistenceAdapter.favorite(userId, third);

            assertThat(List.copyOf(fileFavoritePersistenceAdapter.favoriteFileIds(userId)))
                    .containsExactly(third, second, first);
        }
    }
}
