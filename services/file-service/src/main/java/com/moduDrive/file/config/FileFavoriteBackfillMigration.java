package com.moduDrive.file.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time, idempotent: a favorite is now one {@code file_favorite} row per (user, file) for
 * everyone — that table is the single source of truth the "즐겨찾기" list reads and orders by
 * {@code created_at}. The owner's star used to live only on {@code file.favorite}; this
 * <ol>
 *   <li>backfills those into {@code file_favorite} (so a pre-existing star doesn't vanish), then</li>
 *   <li>drops the now-unused {@code file.favorite} column ({@code ddl-auto=update} adds columns
 *       but never removes one that left the mapping).</li>
 * </ol>
 * This repo has no Flyway/Liquibase (see CLAUDE.md), hence this runner. Each statement is a no-op
 * once applied, safe on every boot.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class FileFavoriteBackfillMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        backfillOwnerStars();
        dropFavoriteColumn();
    }

    private void backfillOwnerStars() {
        try {
            // 'favorite' may already be gone on a boot after the DROP below — then there is
            // nothing to backfill and this whole step is skipped by the catch.
            int inserted = jdbcTemplate.update("""
                    INSERT INTO file_favorite (id, user_id, file_id, created_at)
                    SELECT gen_random_uuid(), f.owner_id, f.id, COALESCE(f.updated_at, now())
                    FROM file f
                    WHERE f.favorite = true
                      AND NOT EXISTS (
                          SELECT 1 FROM file_favorite ff
                          WHERE ff.user_id = f.owner_id AND ff.file_id = f.id
                      )
                    """);
            // Always logged (not just when inserted > 0): a failure here empties every existing
            // user's favorites list, so "ran, 0 rows" must be distinguishable from "threw".
            log.info("file_favorite owner-star backfill: {} row(s) inserted", inserted);
        } catch (Exception e) {
            log.error("file_favorite owner-star backfill FAILED — pre-existing owner favorites "
                    + "will be missing from the favorites list until this succeeds", e);
        }
    }

    private void dropFavoriteColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE file DROP COLUMN IF EXISTS favorite");
        } catch (Exception e) {
            // Harmless: a leftover column that nothing reads. Log and move on.
            log.warn("file.favorite column drop skipped", e);
        }
    }
}
