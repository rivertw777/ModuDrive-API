package com.moduDrive.common.infrastructure.messaging.outbox;

import com.moduDrive.common.infrastructure.messaging.MessagePublisher;
import com.moduDrive.common.infrastructure.messaging.PermanentPublishException;
import com.moduDrive.common.infrastructure.messaging.idempotency.ProcessedEvents;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.observation.transport.SenderContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ClassUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sends PENDING {@code outbox_event} rows, grouped by queue in id order, {@link #SEND_BATCH_SIZE} per broker
 * call, and marks each SENT once the broker accepts it. A transient failure stops the tick after that batch;
 * the failed rows and the rest wait for the next one. A {@link PermanentPublishException} parks the row as
 * FAILED and the tick carries on. A batch call that fails as a whole is retried one row at a time. Either way
 * each message is sent under its recording request's trace, through a relay span and a send span. Ids are assigned at insert, not commit, so concurrent transactions can
 * commit out of id order; no event relies on that order.
 * <p>
 * Runs on its own thread so a send blocked by a broker outage can't stall the services' {@code @Scheduled}
 * jobs on Boot's shared scheduler, and the other way round.
 * <p>
 * The row id is the deduplication id, so a resend after an uncommitted markSent carries the same id.
 * Delivery is at-least-once; consumers deduplicate with {@link ProcessedEvents}.
 * <p>
 * {@code FOR UPDATE SKIP LOCKED} lets several instances run at once without sending a row twice; order
 * holds within one instance's batch only.
 */
@Slf4j
class OutboxRelay {

    static final int BATCH_SIZE = 100;
    // SQS's SendMessageBatch limit.
    static final int SEND_BATCH_SIZE = 10;
    static final Duration SENT_RETENTION = Duration.ofDays(7);
    // Hibernate's value for SKIP LOCKED (org.hibernate.Timeouts.SKIP_LOCKED_MILLI).
    private static final int SKIP_LOCKED = -2;
    private static final TypeReference<Map<String, String>> TRACE_HEADERS = new TypeReference<>() {};

    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private final MessagePublisher messagePublisher;
    private final JsonMapper jsonMapper;
    private final ObservationRegistry observationRegistry;
    private final OutboxMetrics metrics;
    private ScheduledExecutorService executor;

    OutboxRelay(EntityManager entityManager, TransactionTemplate transactionTemplate,
                MessagePublisher messagePublisher, JsonMapper jsonMapper,
                ObservationRegistry observationRegistry, OutboxMetrics metrics) {
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
        this.messagePublisher = messagePublisher;
        this.jsonMapper = jsonMapper;
        this.observationRegistry = observationRegistry;
        this.metrics = metrics;
    }

    // Polls every second, so an event waits up to ~1s.
    void start() {
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("outbox-relay").factory());
        executor.scheduleWithFixedDelay(() -> {
            try {
                relay();
                metrics.refresh();
            } catch (Exception e) {
                // An escaped exception would cancel the schedule for good.
                log.error("Outbox relay tick failed", e);
            }
        }, 1, 1, TimeUnit.SECONDS);
        // Same thread as the relay, so a purge never races a send; hourly keeps each delete small.
        executor.scheduleWithFixedDelay(() -> {
            try {
                purgeSent();
            } catch (Exception e) {
                log.error("Outbox purge failed", e);
            }
        }, 1, 60, TimeUnit.MINUTES);
    }

    void stop() {
        executor.shutdownNow();
    }

    void relay() {
        transactionTemplate.executeWithoutResult(status -> {
            List<OutboxEventJpaEntity> rows = entityManager
                    .createQuery("select e from OutboxEventJpaEntity e where e.status = :pending order by e.id",
                            OutboxEventJpaEntity.class)
                    .setParameter("pending", OutboxEventStatus.PENDING)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .setHint("jakarta.persistence.lock.timeout", SKIP_LOCKED)
                    .setMaxResults(BATCH_SIZE)
                    .getResultList();

            // Rows stay in id order within each queue; no event relies on order across queues.
            Map<String, List<Rebuilt>> byQueue = new LinkedHashMap<>();
            for (OutboxEventJpaEntity row : rows) {
                try {
                    Object event = jsonMapper.readValue(row.getPayload(),
                            ClassUtils.forName(row.getPayloadType(), ClassUtils.getDefaultClassLoader()));
                    byQueue.computeIfAbsent(row.getQueue(), q -> new ArrayList<>()).add(new Rebuilt(row, event));
                } catch (Exception e) {
                    // Retrying won't fix it (e.g. the event class was renamed while the row waited).
                    log.error("Outbox row can't be rebuilt, parking it: id={}, type={}", row.getId(), row.getPayloadType(), e);
                    row.markFailed(e);
                }
            }

            for (Map.Entry<String, List<Rebuilt>> queue : byQueue.entrySet()) {
                List<Rebuilt> pending = queue.getValue();
                for (int from = 0; from < pending.size(); from += SEND_BATCH_SIZE) {
                    if (!sendBatch(queue.getKey(), pending.subList(from, Math.min(from + SEND_BATCH_SIZE, pending.size())))) {
                        return;
                    }
                }
            }
        });
    }

    /** @return false when a transient failure means the rest should wait for the next tick */
    private boolean sendBatch(String queue, List<Rebuilt> batch) {
        List<TracedSend> sends = new ArrayList<>(batch.size());
        Map<String, RuntimeException> failures;
        try {
            for (Rebuilt rebuilt : batch) {
                sends.add(startSend(queue, rebuilt));
            }
            failures = messagePublisher.publishBatch(queue, sends.stream().map(TracedSend::message).toList());
        } catch (Exception e) {
            sends.forEach(send -> send.stop(e));
            // Nothing known sent. One by one finds the row to blame if it's the message (too large, won't
            // serialize), and stops at the first send if it's the broker.
            log.warn("Outbox batch send failed, sending one by one: queue={}, rows={}", queue, batch.size(), e);
            return sendEach(batch);
        }
        boolean carryOn = true;
        for (int i = 0; i < batch.size(); i++) {
            Rebuilt rebuilt = batch.get(i);
            RuntimeException failure = failures.get(deduplicationId(rebuilt.row()));
            sends.get(i).stop(failure);
            if (failure == null) {
                rebuilt.row().markSent();
            } else if (failure instanceof PermanentPublishException) {
                log.error("Outbox row rejected by the broker, parking it: id={}, queue={}",
                        rebuilt.row().getId(), queue, failure);
                rebuilt.row().markFailed(rootCause(failure));
            } else {
                // Its batch-mates already went out, so only this row waits for the next tick.
                log.warn("Outbox send failed, will retry: id={}, queue={}", rebuilt.row().getId(), queue, failure);
                carryOn = false;
            }
        }
        return carryOn;
    }

    private boolean sendEach(List<Rebuilt> batch) {
        for (Rebuilt rebuilt : batch) {
            OutboxEventJpaEntity row = rebuilt.row();
            try {
                send(row, rebuilt.event());
            } catch (PermanentPublishException e) {
                log.error("Outbox row rejected by the broker, parking it: id={}, queue={}",
                        row.getId(), row.getQueue(), e);
                row.markFailed(rootCause(e));
                continue;
            } catch (Exception e) {
                log.warn("Outbox send failed, will retry: id={}, queue={}", row.getId(), row.getQueue(), e);
                return false;
            }
            row.markSent();
        }
        return true;
    }

    /** Deletes SENT rows past {@link #SENT_RETENTION}. PENDING and FAILED rows are never purged. */
    void purgeSent() {
        int purged = transactionTemplate.execute(status -> entityManager
                .createQuery("delete from OutboxEventJpaEntity e where e.status = :sent and e.sentAt < :cutoff")
                .setParameter("sent", OutboxEventStatus.SENT)
                .setParameter("cutoff", Instant.now().minus(SENT_RETENTION))
                .executeUpdate());
        if (purged > 0) {
            log.info("Purged {} sent outbox rows older than {}", purged, SENT_RETENTION);
        }
    }

    /**
     * Opens what {@link #send} gets from Spring Cloud AWS, which doesn't observe batch sends: the relay span under
     * the recording request's trace, and a producer span under it whose context the message carries. Without
     * tracing nothing is injected and the message carries the stored context as is.
     */
    private TracedSend startSend(String queue, Rebuilt rebuilt) {
        Map<String, String> stored = traceHeaders(rebuilt.row());
        ReceiverContext<Map<String, String>> recorded = new ReceiverContext<>(Map::get);
        recorded.setCarrier(stored);
        Observation relay = Observation.createNotStarted("outbox.relay", () -> recorded, observationRegistry)
                .contextualName("outbox relay")
                .start();
        Map<String, String> injected = new HashMap<>();
        SenderContext<Map<String, String>> sending = new SenderContext<>(Map::put);
        sending.setCarrier(injected);
        Observation send = Observation.createNotStarted("outbox.send", () -> sending, observationRegistry)
                .contextualName(queue + " send")
                .parentObservation(relay)
                .start();
        return new TracedSend(relay, send, new MessagePublisher.Outgoing(deduplicationId(rebuilt.row()),
                rebuilt.event(), injected.isEmpty() ? stored : injected));
    }

    /** Unreadable headers cost the trace, not the send: retrying can't fix them and the row would block every tick. */
    private Map<String, String> traceHeaders(OutboxEventJpaEntity row) {
        if (row.getTraceHeaders() == null) {
            return Map.of();
        }
        try {
            return jsonMapper.readValue(row.getTraceHeaders(), TRACE_HEADERS);
        } catch (Exception e) {
            log.warn("Outbox row trace headers unreadable, sending without the trace: id={}", row.getId(), e);
            return Map.of();
        }
    }

    private static String deduplicationId(OutboxEventJpaEntity row) {
        return "outbox-" + row.getId();
    }

    private void send(OutboxEventJpaEntity row, Object event) {
        Map<String, String> traceHeaders = traceHeaders(row);
        // Resumes the recording request's trace. Must be an Observation, not a span in scope: the publisher
        // parents its send span on the current Observation, and the async client wouldn't see a thread-local span.
        ReceiverContext<Map<String, String>> context = new ReceiverContext<>(Map::get);
        context.setCarrier(traceHeaders);
        Observation.createNotStarted("outbox.relay", () -> context, observationRegistry)
                .contextualName("outbox relay")
                .observe(() -> messagePublisher.publish(row.getQueue(), deduplicationId(row), event));
    }

    private record Rebuilt(OutboxEventJpaEntity row, Object event) {
    }

    private record TracedSend(Observation relay, Observation send, MessagePublisher.Outgoing message) {

        void stop(Throwable failure) {
            if (failure != null) {
                send.error(failure);
            }
            send.stop();
            relay.stop();
        }
    }

    /** The broker error itself, not the wrappers around it. */
    private static Throwable rootCause(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
