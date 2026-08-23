package com.moduDrive.file.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time, idempotent fixup for the {@code file} table. This repo has no Flyway/Liquibase and
 * runs on {@code ddl-auto=update} (see CLAUDE.md), which handles adding the
 * {@code active_slot_name} column and the {@code uk_file_namespace_path_active_name} constraint
 * on its own — but it never drops a constraint that disappeared from the mapping (the old
 * {@code uk_file_namespace_path_name}, which this same change replaced) and never backfills a
 * newly-added column on rows that already existed. Both are required for the new constraint to
 * actually protect a database that already has data in it:
 * <ul>
 *   <li>leaving the old constraint in place keeps blocking a re-upload of a trashed file's name —
 *       the exact bug this change exists to fix;</li>
 *   <li>leaving existing active rows with a NULL {@code active_slot_name} drops them out of the
 *       new constraint's protection entirely (NULLs are mutually distinct), so two active rows
 *       could end up sharing a slot until this backfill runs.</li>
 * </ul>
 * Safe to run on every boot: each statement is a no-op once already applied.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class FileActiveSlotNameMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.update(
                    "UPDATE file SET active_slot_name = name WHERE status <> 'DELETED' AND active_slot_name IS NULL");
            jdbcTemplate.execute("ALTER TABLE file DROP CONSTRAINT IF EXISTS uk_file_namespace_path_name");
        } catch (Exception e) {
            // Best-effort: never block app startup over cleanup of a constraint that may already
            // be gone, or a dialect that phrases either statement differently than expected.
            log.warn("file.active_slot_name backfill / legacy-constraint cleanup skipped", e);
        }
    }
}
