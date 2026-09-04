package com.moduDrive.file.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time, idempotent trash-lifecycle fixup for the {@code file} table.
 * <ol>
 *   <li>Backfills {@code trashed_at} for files already in the trash — before this column existed
 *       the retention sweep used {@code updated_at}, so that's the best available "trashed on"
 *       value. Without it every pre-existing trashed file drops out of both the trash view and
 *       the retention sweep (both now key on {@code trashed_at}).</li>
 *   <li>Drops {@code ix_file_status_updated_at} — the sweep keys on {@code trashed_at} now, so
 *       that index is dead weight ({@code ddl-auto=update} never drops an index).</li>
 * </ol>
 * This repo has no Flyway/Liquibase (see CLAUDE.md), and {@code ddl-auto=update} adds columns but
 * never backfills. Each statement is a no-op once applied; safe on every boot.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class FileTrashLifecycleMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        backfillTrashedAt();
        dropStaleIndex();
    }

    private void backfillTrashedAt() {
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE file SET trashed_at = updated_at WHERE status = 'DELETED' AND trashed_at IS NULL");
            // Always logged: a miss here drops pre-existing trashed rows out of BOTH the trash
            // view AND the retention sweep (both key on trashed_at) — so "ran, 0 rows" has to be
            // distinguishable from "threw".
            log.info("file.trashed_at backfill: {} row(s) updated", updated);
        } catch (Exception e) {
            log.error("file.trashed_at backfill FAILED — pre-existing trashed files will be "
                    + "missing from the trash view AND never auto-purged (their blocks leak) "
                    + "until this succeeds", e);
        }
    }

    private void dropStaleIndex() {
        try {
            jdbcTemplate.execute("DROP INDEX IF EXISTS ix_file_status_updated_at");
        } catch (Exception e) {
            // A leftover index that nothing uses — log and move on.
            log.warn("ix_file_status_updated_at drop skipped", e);
        }
    }
}
