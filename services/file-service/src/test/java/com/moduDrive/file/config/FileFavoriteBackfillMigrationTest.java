package com.moduDrive.file.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class FileFavoriteBackfillMigrationTest {

    private JdbcTemplate jdbcTemplate;
    private FileFavoriteBackfillMigration migration;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setDriverClassName("org.h2.Driver");
        jdbcTemplate = new JdbcTemplate(dataSource);
        migration = new FileFavoriteBackfillMigration(jdbcTemplate);
    }

    @Nested
    @DisplayName("file_favorite 테이블이 준비돼 있을 때")
    class WhenSchemaReady {

        @BeforeEach
        void seedSchema() {
            jdbcTemplate.execute("""
                    CREATE TABLE file (
                        id UUID PRIMARY KEY, owner_id UUID, favorite BOOLEAN,
                        updated_at TIMESTAMP)
                    """);
            jdbcTemplate.execute("""
                    CREATE TABLE file_favorite (
                        id UUID PRIMARY KEY, user_id UUID, file_id UUID, created_at TIMESTAMP,
                        CONSTRAINT uk_file_favorite_user_file UNIQUE (user_id, file_id))
                    """);
        }

        private void insertFile(UUID id, UUID ownerId, boolean favorite, LocalDateTime updatedAt) {
            jdbcTemplate.update("INSERT INTO file (id, owner_id, favorite, updated_at) VALUES (?, ?, ?, ?)",
                    id, ownerId, favorite, updatedAt);
        }

        @Test
        @DisplayName("소유자가 별표한 파일마다 file_favorite 행을 만들고 created_at을 파일 updated_at에서 가져온다")
        void backfillsOwnerFavorites() {
            UUID ownerId = UUID.randomUUID();
            UUID favoritedFileId = UUID.randomUUID();
            LocalDateTime updatedAt = LocalDateTime.now().minusDays(3).withNano(0);
            insertFile(favoritedFileId, ownerId, true, updatedAt);
            insertFile(UUID.randomUUID(), ownerId, false, LocalDateTime.now());

            migration.run(null);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM file_favorite", Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT created_at FROM file_favorite WHERE user_id = ? AND file_id = ?",
                    LocalDateTime.class, ownerId, favoritedFileId)).isEqualTo(updatedAt);
        }

        @Test
        @DisplayName("이미 백필된 상태에서 다시 돌려도 행이 중복되지 않는다")
        void isIdempotent() {
            UUID ownerId = UUID.randomUUID();
            insertFile(UUID.randomUUID(), ownerId, true, LocalDateTime.now());

            migration.run(null);
            migration.run(null);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM file_favorite", Integer.class)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("신규 DB 라 file_favorite 테이블이 아직 없을 때")
    class WhenFreshDatabase {

        @Test
        @DisplayName("오류를 삼키고 애플리케이션 기동을 막지 않는다")
        void neverPropagatesTheMissingTable() {
            assertThatCode(() -> migration.run(null)).doesNotThrowAnyException();
        }
    }
}
