package com.moduDrive.common.infrastructure.messaging.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;

import java.time.Duration;
import java.time.Instant;

/**
 * Publishes the two numbers that say whether the outbox is healthy: how far behind sending is, as the
 * age in seconds of the oldest row still waiting, and how many rows are parked for a human.
 * <p>
 * Neither state turns red on its own. {@link OutboxRelay} retries a failure that isn't the message's
 * own fault without a limit, on purpose — a limit would park healthy rows by the hundred during an
 * outage and leave a human to put them back — so a broker outage leaves nothing behind but rows that
 * look, in the table, exactly like rows that arrived a second ago. And a {@code FAILED} row stays
 * until someone deals with it, silently. Alert on these instead: the lag sits near a second in normal
 * running and climbs for as long as sending is stuck, and the failed count is 0 or a problem.
 */
class OutboxMetrics {

    static final String LAG_METER_NAME = "modudrive.outbox.lag";
    static final String FAILED_METER_NAME = "modudrive.outbox.failed";

    private final EntityManager entityManager;

    OutboxMetrics(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    void bindTo(MeterRegistry registry) {
        Gauge.builder(LAG_METER_NAME, this, OutboxMetrics::oldestPendingAgeSeconds)
                .description("Age of the oldest outbox row still waiting to be sent")
                .baseUnit("seconds")
                .register(registry);
        Gauge.builder(FAILED_METER_NAME, this, OutboxMetrics::failedRows)
                .description("Outbox rows parked for a human to deal with")
                .register(registry);
    }

    /** 0 when nothing is waiting. */
    // ponytail: reads every PENDING row per scrape. They're few unless sending is stuck, which is
    // exactly when the alert has already fired; add created_at to the partial index if that changes.
    double oldestPendingAgeSeconds() {
        Instant oldest = entityManager
                .createQuery("select min(e.createdAt) from OutboxEventJpaEntity e where e.status = :pending",
                        Instant.class)
                .setParameter("pending", OutboxEventStatus.PENDING)
                .getSingleResult();
        return oldest == null ? 0 : Duration.between(oldest, Instant.now()).toMillis() / 1000d;
    }

    double failedRows() {
        return entityManager
                .createQuery("select count(e) from OutboxEventJpaEntity e where e.status = :failed", Long.class)
                .setParameter("failed", OutboxEventStatus.FAILED)
                .getSingleResult();
    }
}
