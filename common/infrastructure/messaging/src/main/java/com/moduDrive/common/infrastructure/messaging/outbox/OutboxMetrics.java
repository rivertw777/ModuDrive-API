package com.moduDrive.common.infrastructure.messaging.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;

import jakarta.persistence.EntityManager;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes the two numbers that say whether the outbox is healthy: how far behind sending is, as the
 * age in seconds of the oldest row still waiting, and how many rows are parked for a human, per queue.
 * <p>
 * Neither state turns red on its own. {@link OutboxRelay} retries a failure that isn't the message's
 * own fault without a limit, on purpose — a limit would park healthy rows by the hundred during an
 * outage and leave a human to put them back — so a broker outage leaves nothing behind but rows that
 * look, in the table, exactly like rows that arrived a second ago. And a {@code FAILED} row stays
 * until someone deals with it, silently. Alert on these instead: the lag sits near a second in normal
 * running and climbs for as long as sending is stuck, and the failed count is 0 or a problem.
 * <p>
 * The failed count carries the queue as a tag so the alert can name the events that are stuck instead
 * of only the service. It can't be read on scrape like the lag — the set of queues changes — so the
 * relay refreshes it on its tick.
 */
class OutboxMetrics {

    static final String LAG_METER_NAME = "modudrive.outbox.lag";
    static final String FAILED_METER_NAME = "modudrive.outbox.failed";
    private static final int MAX_PARKED_ROWS_READ = 500;
    private static final int MAX_DETAIL_LENGTH = 100;

    private final EntityManager entityManager;
    private MultiGauge failedByQueue;

    OutboxMetrics(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    void bindTo(MeterRegistry registry) {
        Gauge.builder(LAG_METER_NAME, this, OutboxMetrics::oldestPendingAgeSeconds)
                .description("Age of the oldest outbox row still waiting to be sent")
                .baseUnit("seconds")
                .register(registry);
        failedByQueue = MultiGauge.builder(FAILED_METER_NAME)
                .description("Outbox rows parked for a human to deal with")
                .register(registry);
        refreshFailed();
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

    /**
     * Republishes one series per queue and failure. A group whose rows were fixed drops out of the
     * result and {@link MultiGauge} removes its series, which is what resolves the alert.
     */
    void refreshFailed() {
        if (failedByQueue == null) {
            return; // No registry, so nothing is published and there's nothing to refresh.
        }
        failedByQueue.register(failedGroups().stream()
                .map(group -> MultiGauge.Row.of(group.tags(), group.count()))
                .toList(), true);
    }

    /**
     * Parked rows counted per queue and failure. The alert can name the queue and what went wrong; the
     * rows themselves are read with the query it carries.
     */
    // ponytail: reads the parked rows rather than grouping in SQL, because the reason has to be cut
    // down to the exception class first. They're a handful — permanent failures are per-message, not
    // per-outage — and the cap keeps a pathological case from being read every second.
    List<FailedGroup> failedGroups() {
        List<Object[]> rows = entityManager
                .createQuery("select e.queue, e.failureReason from OutboxEventJpaEntity e "
                        + "where e.status = :failed order by e.id", Object[].class)
                .setParameter("failed", OutboxEventStatus.FAILED)
                .setMaxResults(MAX_PARKED_ROWS_READ)
                .getResultList();

        record Key(String queue, String reason, String detail) {}
        Map<Key, Long> counted = new LinkedHashMap<>();
        for (Object[] row : rows) {
            counted.merge(new Key((String) row[0], exceptionClass((String) row[1]), detail((String) row[1])),
                    1L, Long::sum);
        }
        return counted.entrySet().stream()
                .map(entry -> new FailedGroup(entry.getKey().queue(), entry.getKey().reason(),
                        entry.getKey().detail(), entry.getValue()))
                .toList();
    }

    /** {@code java.lang.IllegalStateException: too large} → {@code IllegalStateException}. */
    private static String exceptionClass(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            return "unknown";
        }
        String type = failureReason.split(":", 2)[0].trim();
        return type.substring(type.lastIndexOf('.') + 1);
    }

    /**
     * The exception's own message, so the alert can say <em>message rejected</em> rather than only the
     * type. Cut short because it ends up as a label value: a message that carries an id or a byte count
     * would otherwise start a new series for every row.
     */
    private static String detail(String failureReason) {
        if (failureReason == null || !failureReason.contains(":")) {
            return "";
        }
        String message = failureReason.split(":", 2)[1].replaceAll("\\s+", " ").trim();
        return message.length() <= MAX_DETAIL_LENGTH ? message : message.substring(0, MAX_DETAIL_LENGTH) + "…";
    }

    record FailedGroup(String queue, String reason, String detail, long count) {

        Tags tags() {
            return Tags.of("queue", queue, "reason", reason, "detail", detail);
        }
    }
}
