package com.moduDrive.common.infrastructure.messaging.outbox;

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
 * Per-queue outbox health: age of the oldest PENDING row (lag) and the FAILED row count. The relay retries
 * transient failures without limit, so a broker outage only shows up as growing lag, and a FAILED row stays
 * silent until someone looks; alerts read these. The relay refreshes them on its tick because the set of
 * queues changes as rows pile up and drain.
 */
class OutboxMetrics {

    static final String LAG_METER_NAME = "modudrive.outbox.lag";
    static final String FAILED_METER_NAME = "modudrive.outbox.failed";
    private static final int MAX_PARKED_ROWS_READ = 500;
    private static final int MAX_DETAIL_LENGTH = 100;

    private final EntityManager entityManager;
    private MultiGauge lagByQueue;
    private MultiGauge failedByQueue;

    OutboxMetrics(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    void bindTo(MeterRegistry registry) {
        lagByQueue = MultiGauge.builder(LAG_METER_NAME)
                .description("Age of the oldest outbox row still waiting to be sent")
                .baseUnit("seconds")
                .register(registry);
        failedByQueue = MultiGauge.builder(FAILED_METER_NAME)
                .description("Outbox rows parked for a human to deal with")
                .register(registry);
        // No query here: the relay refreshes every second from its first tick, and creating the bean
        // shouldn't need the database (the image's AOT training run has none).
    }

    void refresh() {
        refreshLag();
        refreshFailed();
    }

    /** One series per queue with rows waiting; a drained queue's series is removed, resolving the alert. */
    void refreshLag() {
        if (lagByQueue == null) {
            return; // no MeterRegistry
        }
        lagByQueue.register(pendingLag().stream()
                .map(group -> MultiGauge.Row.of(Tags.of("queue", group.queue()), group.ageSeconds()))
                .toList(), true);
    }

    /** How long the oldest row still waiting has waited, per queue. Empty when nothing is waiting. */
    List<LagGroup> pendingLag() {
        Instant now = Instant.now();
        return entityManager
                .createQuery("select e.queue, min(e.createdAt) from OutboxEventJpaEntity e "
                        + "where e.status = :pending group by e.queue", Object[].class)
                .setParameter("pending", OutboxEventStatus.PENDING)
                .getResultList().stream()
                .map(row -> new LagGroup((String) row[0],
                        Duration.between((Instant) row[1], now).toMillis() / 1000d))
                .toList();
    }

    /** One series per queue and failure; a fixed group's series is removed, resolving the alert. */
    void refreshFailed() {
        if (failedByQueue == null) {
            return; // no MeterRegistry
        }
        failedByQueue.register(failedGroups().stream()
                .map(group -> MultiGauge.Row.of(group.tags(), group.count()))
                .toList(), true);
    }

    /** FAILED rows counted per queue and failure. Grouped in Java because the reason must be cut down to
     * the exception class first; the cap keeps a pathological pile from being read every second. */
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

    /** The exception message, cut short because it becomes a label value. The AWS SDK's
     * {@code (Service: Sqs, ... Request ID: …)} tail is dropped since the request id differs per row. */
    private static String detail(String failureReason) {
        if (failureReason == null || !failureReason.contains(":")) {
            return "";
        }
        String message = failureReason.split(":", 2)[1]
                .replaceFirst(" \\(Service: .*", "")
                .replaceAll("\\s+", " ").trim();
        return message.length() <= MAX_DETAIL_LENGTH ? message : message.substring(0, MAX_DETAIL_LENGTH) + "…";
    }

    record LagGroup(String queue, double ageSeconds) {}

    record FailedGroup(String queue, String reason, String detail, long count) {

        Tags tags() {
            return Tags.of("queue", queue, "reason", reason, "detail", detail);
        }
    }
}
