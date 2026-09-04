package com.moduDrive.file.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time, idempotent rename of {@code file.directory} to {@code file.is_directory}.
 * {@code ddl-auto=update} adds the new {@code is_directory} column (see {@code FileJpaEntity})
 * but never renames or drops the old one, so on a database that already has data this
 * <ol>
 *   <li>copies {@code directory} into the freshly-added {@code is_directory}, then</li>
 *   <li>drops the old {@code directory} column.</li>
 * </ol>
 * On a fresh database {@code directory} never existed — the first statement fails, is caught, and
 * the whole thing is a no-op (ddl-auto already made {@code is_directory}). Safe on every boot.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class FileDirectoryColumnRenameMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int copied = jdbcTemplate.update(
                    "UPDATE file SET is_directory = directory WHERE is_directory IS NULL");
            if (copied > 0) {
                log.info("Copied file.directory -> file.is_directory for {} row(s)", copied);
            }
            jdbcTemplate.execute("ALTER TABLE file DROP COLUMN IF EXISTS directory");
        } catch (Exception e) {
            // Fresh DB (no legacy `directory` column) or already applied — either way nothing to do.
            log.warn("file.directory -> is_directory rename skipped", e);
        }
    }
}
