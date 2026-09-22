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
 * Both carry the queue as a tag so an alert can name the events that are stuck instead of only the
 * service. Neither can be read on scrape, because the set of queues changes as rows pile up and drain,
 * so the relay refreshes them on its tick.
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
        refresh();
    }

    void refresh() {
        refreshLag();
        refreshFailed();
    }

    /**
     * Republishes one series per queue that has something waiting. A queue that drains drops out and
     * its series is removed, which resolves the alert — the same way the parked count works.
     */
    void refreshLag() {
        if (lagByQueue == null) {
            return; // No registry, so nothing is published and there's nothing to refresh.
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

    record LagGroup(String queue, double ageSeconds) {}

    record FailedGroup(String queue, String reason, String detail, long count) {

        Tags tags() {
            return Tags.of("queue", queue, "reason", reason, "detail", detail);
        }
    }
}
