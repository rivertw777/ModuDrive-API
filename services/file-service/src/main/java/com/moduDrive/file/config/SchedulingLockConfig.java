package com.moduDrive.file.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/** Cross-cutting, not owned by one adapter — the lock a {@code @SchedulerLock}-annotated method
 * anywhere in the app acquires, so this sits at the root like the other shared config classes.
 * {@code shedlock-provider-jdbc-template} works against any JDBC datasource (unlike a
 * Postgres-only {@code pg_advisory_lock}), so this survives swapping the database vendor —
 * only the {@code shedlock} table (see {@code ShedLockJpaEntity}) needs to exist. */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
class SchedulingLockConfig {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}
