package com.moduDrive.file.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class FileActiveSlotNameMigrationTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @InjectMocks
    private FileActiveSlotNameMigration migration;

    @Nested
    @DisplayName("정상적으로 실행될 때")
    class WhenStatementsSucceed {

        @Test
        void backfillsThenDropsTheLegacyConstraint() {
            migration.run(null);

            then(jdbcTemplate).should().update(
                    "UPDATE file SET active_slot_name = name WHERE status <> 'DELETED' AND active_slot_name IS NULL");
            then(jdbcTemplate).should().execute(
                    "ALTER TABLE file DROP CONSTRAINT IF EXISTS uk_file_namespace_path_name");
        }
    }

    @Nested
    @DisplayName("문(statement) 실행이 실패할 때")
    class WhenAStatementFails {

        @Test
        @DisplayName("예외를 삼키고 애플리케이션 기동을 막지 않는다")
        void neverPropagatesTheFailure() {
            given(jdbcTemplate.update(anyString())).willThrow(new RuntimeException("dialect mismatch"));

            assertThatCode(() -> migration.run(null)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("DROP CONSTRAINT 실패도 삼킨다")
        void neverPropagatesADropFailure() {
            willThrow(new RuntimeException("syntax error")).given(jdbcTemplate).execute(anyString());

            assertThatCode(() -> migration.run(null)).doesNotThrowAnyException();
        }
    }
}
