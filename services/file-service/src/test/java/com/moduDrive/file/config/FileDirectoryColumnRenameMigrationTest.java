package com.moduDrive.file.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class FileDirectoryColumnRenameMigrationTest {

    private JdbcTemplate jdbcTemplate;
    private FileDirectoryColumnRenameMigration migration;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setDriverClassName("org.h2.Driver");
        jdbcTemplate = new JdbcTemplate(dataSource);
        migration = new FileDirectoryColumnRenameMigration(jdbcTemplate);
    }

    @Nested
    @DisplayName("레거시 directory 컬럼이 남아있을 때")
    class WhenLegacyColumnExists {

        @BeforeEach
        void seedLegacySchema() {
            // ddl-auto has already added the new column (nullable) alongside the old one.
            jdbcTemplate.execute("CREATE TABLE file (id UUID PRIMARY KEY, directory BOOLEAN, is_directory BOOLEAN)");
        }

        @Test
        @DisplayName("directory 값을 is_directory로 복사하고 구 컬럼을 드롭한다")
        void copiesThenDropsTheOldColumn() {
            UUID dirId = UUID.randomUUID();
            UUID fileId = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO file (id, directory, is_directory) VALUES (?, true, NULL)", dirId);
            jdbcTemplate.update("INSERT INTO file (id, directory, is_directory) VALUES (?, false, NULL)", fileId);

            migration.run(null);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT is_directory FROM file WHERE id = ?", Boolean.class, dirId)).isTrue();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT is_directory FROM file WHERE id = ?", Boolean.class, fileId)).isFalse();
            assertThat(jdbcTemplate.queryForList(
                    "SELECT column_name FROM information_schema.columns WHERE table_name = 'FILE'", String.class))
                    .doesNotContain("DIRECTORY");
        }

        @Test
        @DisplayName("다시 돌려도 오류 없이 끝난다")
        void isIdempotent() {
            jdbcTemplate.update("INSERT INTO file (id, directory, is_directory) VALUES (?, true, NULL)", UUID.randomUUID());

            migration.run(null);
            assertThatCode(() -> migration.run(null)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("신규 DB 라 directory 컬럼이 없을 때")
    class WhenFreshDatabase {

        @BeforeEach
        void seedFreshSchema() {
            jdbcTemplate.execute("CREATE TABLE file (id UUID PRIMARY KEY, is_directory BOOLEAN NOT NULL)");
        }

        @Test
        @DisplayName("오류를 삼키고 애플리케이션 기동을 막지 않는다")
        void neverPropagatesTheMissingColumn() {
            assertThatCode(() -> migration.run(null)).doesNotThrowAnyException();
        }
    }
}
