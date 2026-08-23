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

class FileShareGrantedRoleMigrationTest {

    private JdbcTemplate jdbcTemplate;
    private FileShareGrantedRoleMigration migration;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setDriverClassName("org.h2.Driver");
        jdbcTemplate = new JdbcTemplate(dataSource);
        migration = new FileShareGrantedRoleMigration(jdbcTemplate);
    }

    @Nested
    @DisplayName("레거시 file_role 테이블이 남아있을 때")
    class WhenLegacyFileRoleTableExists {

        @BeforeEach
        void seedLegacySchema() {
            jdbcTemplate.execute("CREATE TABLE file_role (id UUID PRIMARY KEY, role_name VARCHAR(255))");
            jdbcTemplate.execute(
                    "CREATE TABLE file_share (id UUID PRIMARY KEY, granted_role_id UUID, granted_role VARCHAR(255))");
        }

        @Test
        @DisplayName("granted_role_id 로 참조되는 role_name 을 granted_role 에 채운다")
        void backfillsGrantedRoleFromLegacyRoleId() {
            UUID roleId = UUID.randomUUID();
            UUID shareId = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO file_role (id, role_name) VALUES (?, ?)", roleId, "EDITOR");
            jdbcTemplate.update(
                    "INSERT INTO file_share (id, granted_role_id, granted_role) VALUES (?, ?, NULL)", shareId, roleId);

            migration.run(null);

            String grantedRole = jdbcTemplate.queryForObject(
                    "SELECT granted_role FROM file_share WHERE id = ?", String.class, shareId);
            assertThat(grantedRole).isEqualTo("EDITOR");
        }

        @Test
        @DisplayName("이미 granted_role 이 채워진 행은 덮어쓰지 않는다")
        void doesNotOverwriteAnAlreadySetRole() {
            UUID roleId = UUID.randomUUID();
            UUID shareId = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO file_role (id, role_name) VALUES (?, ?)", roleId, "EDITOR");
            jdbcTemplate.update(
                    "INSERT INTO file_share (id, granted_role_id, granted_role) VALUES (?, ?, ?)",
                    shareId, roleId, "VIEWER");

            migration.run(null);

            String grantedRole = jdbcTemplate.queryForObject(
                    "SELECT granted_role FROM file_share WHERE id = ?", String.class, shareId);
            assertThat(grantedRole).isEqualTo("VIEWER");
        }
    }

    @Nested
    @DisplayName("신규 DB 라 file_role 테이블이 없을 때")
    class WhenFreshDatabase {

        @BeforeEach
        void seedFreshSchema() {
            jdbcTemplate.execute("CREATE TABLE file_share (id UUID PRIMARY KEY, granted_role VARCHAR(255))");
        }

        @Test
        @DisplayName("테이블 없음 오류를 삼키고 애플리케이션 기동을 막지 않는다")
        void neverPropagatesTheMissingTable() {
            assertThatCode(() -> migration.run(null)).doesNotThrowAnyException();
        }
    }
}
