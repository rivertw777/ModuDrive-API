package com.moduDrive.common.infrastructure.messaging.idempotency;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against real Postgres: the unique constraint and "rolls back with the caller" are the whole
 * point, and neither shows up against a mock. */
class JpaProcessedEventsTest {

    private static final String QUEUE = "member-signed-up";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static ConfigurableApplicationContext context;
    private static EntityManager entityManager;
    private static TransactionTemplate transactionTemplate;
    private JpaProcessedEvents processedEvents;

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
        context = new SpringApplicationBuilder(EntityScanConfig.class)
                .sources(DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
                        TransactionAutoConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(),
                        "spring.jpa.hibernate.ddl-auto=create-drop")
                .run();
        entityManager = SharedEntityManagerCreator.createSharedEntityManager(context.getBean(EntityManagerFactory.class));
        transactionTemplate = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    @AfterAll
    static void stopDatabase() {
        context.close();
        POSTGRES.stop();
    }

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status ->
                entityManager.createQuery("delete from ProcessedEventJpaEntity").executeUpdate());
        processedEvents = new JpaProcessedEvents(entityManager, transactionTemplate);
    }

    @Nested
    @DisplayName("메시지를 처리했다고 기록할 때")
    class WhenMarkingProcessed {

        @Test
        @DisplayName("같은 큐·중복 제거 id는 두 번째부터 이미 처리한 것으로 본다")
        void recognisesTheSameMessageNextTime() {
            transactionTemplate.executeWithoutResult(status -> {
                assertThat(processedEvents.isProcessed(QUEUE, "outbox-1")).isFalse();
                processedEvents.markProcessed(QUEUE, "outbox-1");
            });

            transactionTemplate.executeWithoutResult(status -> {
                assertThat(processedEvents.isProcessed(QUEUE, "outbox-1")).isTrue();
                // Same id on another queue is a different event.
                assertThat(processedEvents.isProcessed("mail-verification-requested", "outbox-1")).isFalse();
            });
        }

        @Test
        @DisplayName("호출자의 트랜잭션이 롤백되면 기록도 남지 않아 다음 수신 때 다시 처리한다")
        void rollsBackWithTheCallersTransaction() {
            transactionTemplate.executeWithoutResult(status -> {
                processedEvents.markProcessed(QUEUE, "outbox-2");
                status.setRollbackOnly();
            });

            transactionTemplate.executeWithoutResult(status ->
                    assertThat(processedEvents.isProcessed(QUEUE, "outbox-2")).isFalse());
        }
    }

    @Nested
    @DisplayName("오래된 기록을 정리할 때")
    class WhenPurging {

        @Test
        @DisplayName("보관 기간이 지난 기록만 지운다")
        void deletesOnlyRecordsPastRetention() {
            transactionTemplate.executeWithoutResult(status -> {
                processedEvents.markProcessed(QUEUE, "old");
                processedEvents.markProcessed(QUEUE, "recent");
            });
            transactionTemplate.executeWithoutResult(status -> entityManager
                    .createQuery("update ProcessedEventJpaEntity e set e.processedAt = :old where e.deduplicationId = 'old'")
                    .setParameter("old", Instant.now().minus(ProcessedEvents.RETENTION).minusSeconds(60))
                    .executeUpdate());

            processedEvents.purgeExpired();

            transactionTemplate.executeWithoutResult(status -> {
                assertThat(processedEvents.isProcessed(QUEUE, "old")).isFalse();
                assertThat(processedEvents.isProcessed(QUEUE, "recent")).isTrue();
            });
        }
    }

    @Configuration
    @AutoConfigurationPackage
    static class EntityScanConfig {
    }
}
