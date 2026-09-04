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

class FileTrashLifecycleMigrationTest {

    private JdbcTemplate jdbcTemplate;
    private FileTrashLifecycleMigration migration;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setDriverClassName("org.h2.Driver");
        jdbcTemplate = new JdbcTemplate(dataSource);
        migration = new FileTrashLifecycleMigration(jdbcTemplate);
    }

    @Nested
    @DisplayName("레거시 스키마일 때")
    class WhenLegacySchema {

        @BeforeEach
        void seedLegacySchema() {
            // ddl-auto already added trashed_at / deleted_at; is_deleted is the BaseTimeEntity leftover.
            jdbcTemplate.execute("""
                    CREATE TABLE file (
                        id UUID PRIMARY KEY, status VARCHAR(20), updated_at TIMESTAMP,
                        trashed_at TIMESTAMP, deleted_at TIMESTAMP, is_deleted BOOLEAN)
                    """);
        }

        @Test
        @DisplayName("휴지통에 있는 파일의 trashed_at을 updated_at으로 채우고 is_deleted 컬럼을 드롭한다")
        void backfillsTrashedAtAndDropsIsDeleted() {
            UUID trashed = UUID.randomUUID();
            UUID active = UUID.randomUUID();
            LocalDateTime trashedOn = LocalDateTime.now().minusDays(2).withNano(0);
            jdbcTemplate.update(
                    "INSERT INTO file (id, status, updated_at, trashed_at) VALUES (?, 'DELETED', ?, NULL)",
                    trashed, trashedOn);
            jdbcTemplate.update(
                    "INSERT INTO file (id, status, updated_at, trashed_at) VALUES (?, 'UPLOADED', ?, NULL)",
                    active, LocalDateTime.now());

            migration.run(null);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT trashed_at FROM file WHERE id = ?", LocalDateTime.class, trashed)).isEqualTo(trashedOn);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT trashed_at FROM file WHERE id = ?", LocalDateTime.class, active)).isNull();
            assertThat(jdbcTemplate.queryForList(
                    "SELECT column_name FROM information_schema.columns WHERE table_name = 'FILE'", String.class))
                    .doesNotContain("IS_DELETED");
        }

        @Test
        @DisplayName("다시 돌려도 오류 없이 끝난다")
        void isIdempotent() {
            jdbcTemplate.update(
                    "INSERT INTO file (id, status, updated_at, trashed_at) VALUES (?, 'DELETED', ?, NULL)",
                    UUID.randomUUID(), LocalDateTime.now());

            migration.run(null);
            assertThatCode(() -> migration.run(null)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("신규 DB 라 file 테이블이 아직 없을 때")
    class WhenFreshDatabase {

        @Test
        @DisplayName("오류를 삼키고 애플리케이션 기동을 막지 않는다")
        void neverPropagatesTheMissingTable() {
            assertThatCode(() -> migration.run(null)).doesNotThrowAnyException();
        }
    }
}
