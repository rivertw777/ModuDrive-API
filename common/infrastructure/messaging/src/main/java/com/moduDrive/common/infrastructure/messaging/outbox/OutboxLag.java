package com.moduDrive.common.infrastructure.messaging.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;

import java.time.Duration;
import java.time.Instant;

/**
 * Publishes how far behind sending is, as the age in seconds of the oldest row still waiting.
 * <p>
 * This is the only sign of a broker outage. {@link OutboxRelay} retries a failure that isn't the
 * message's own fault without a limit, on purpose — a limit would park healthy rows by the hundred
 * during an outage and leave a human to put them back. Nothing turns red on its own, so alert on this
 * number instead: in normal running it sits near a second, and it climbs for as long as sending is
 * stuck.
 */
class OutboxLag {

    static final String METER_NAME = "modudrive.outbox.lag";

    private final EntityManager entityManager;

    OutboxLag(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    void bindTo(MeterRegistry registry) {
        Gauge.builder(METER_NAME, this, OutboxLag::oldestPendingAgeSeconds)
                .description("Age of the oldest outbox row still waiting to be sent")
                .baseUnit("seconds")
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
}
