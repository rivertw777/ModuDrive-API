package com.moduDrive.file.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time, idempotent backfill: the owner's stars used to live only on {@code file.favorite}, but
 * a favorite is now one {@code file_favorite} row per (user, file) for everyone — that table is
 * what the "즐겨찾기" list reads and orders by {@code created_at}. Without this, an owner's
 * pre-existing stars would vanish from the list until they re-star each file.
 *
 * <p>{@code file.favorite} stays as the denormalized flag the owner-facing listings read cheaply;
 * {@link com.moduDrive.file.application.service.UpdateFileFavoriteService} keeps the two in sync
 * from here on. This repo has no Flyway/Liquibase and runs on {@code ddl-auto=update} (see
 * CLAUDE.md), which creates the {@code created_at} column but never backfills rows — hence this
 * runner. The {@code NOT EXISTS} guard makes it a no-op once applied, safe on every boot.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class FileFavoriteBackfillMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
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
            // Best-effort: never block startup over a backfill (e.g. a dialect without
            // gen_random_uuid(), or file_favorite not yet created on a brand-new DB) — but log
            // loudly, because unlike the sibling runners a miss here loses user data from a view.
            log.error("file_favorite owner-star backfill FAILED — pre-existing owner favorites "
                    + "will be missing from the favorites list until this succeeds", e);
        }
    }
}
