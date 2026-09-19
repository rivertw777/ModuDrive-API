package com.moduDrive.member.adapter.out.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Every JPA test already runs db/migration + validate; this one adds the dev-only db/seed, which
 * no other test loads, so a broken seed fails here instead of on the next local startup. Its own
 * jdbc:tc database name gets its own container, so the seed rows never leak into other tests. */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:18-alpine:///seed",
        "spring.flyway.locations=classpath:db/migration,classpath:db/seed"})
class FlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    @DisplayName("dev 시드까지 적용해도 모든 마이그레이션이 성공한다")
    void appliesMigrationsAndDevSeed() {
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied()).extracting(m -> m.getVersion().getVersion()).contains("1.1");
    }
}
