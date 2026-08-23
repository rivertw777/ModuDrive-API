package com.moduDrive.file.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time, idempotent fixup for {@code file_share.granted_role}. The old {@code granted_role_id}
 * FK column (pointing at the now-removed {@code file_role} table) was replaced by an inline
 * {@code granted_role} enum column, but ddl-auto=update only adds the new column — it never
 * backfills it from the old FK on rows that already existed. Left unfixed, every pre-existing
 * share row reads back {@code granted_role = NULL}, and {@link com.moduDrive.file.adapter.out.persistence.FileMapper}
 * falls back to {@code Role.VIEWER} for that, silently downgrading any existing EDITOR grant.
 *
 * <p>A fresh database never had a {@code file_role} table, so the backfill query fails with a
 * missing-table error there; that failure is swallowed the same way the rest of this fixup
 * tolerates a dialect it doesn't expect. Safe to run on every boot: a no-op once every row is
 * filled in.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class FileShareGrantedRoleMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int updated = jdbcTemplate.update("""
                    UPDATE file_share fs SET granted_role = fr.role_name
                    FROM file_role fr
                    WHERE fs.granted_role_id = fr.id AND fs.granted_role IS NULL
                    """);
            if (updated > 0) {
                log.info("Backfilled file_share.granted_role for {} pre-existing row(s)", updated);
            }
        } catch (Exception e) {
            // Best-effort: a fresh database has no file_role table to join against, and that
            // missing-table error must never block app startup.
            log.warn("file_share.granted_role backfill skipped", e);
        }
    }
}
