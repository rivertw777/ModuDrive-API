package com.moduDrive.common.infrastructure.outbox;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.ReceiverContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.extern.slf4j.Slf4j;
import com.moduDrive.common.infrastructure.sqs.SqsFailures;
import io.awspring.cloud.sqs.listener.SqsHeaders.MessageSystemAttributes;
import io.awspring.cloud.sqs.operations.SqsOperations;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ClassUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sends PENDING {@code outbox_event} rows to SQS (FIFO queues) in id order and marks each one SENT
 * after SQS accepts it. SENT rows are purged once they're older than {@link #SENT_RETENTION}. If a send fails because SQS is unreachable (or throttling, 5xx, access denied), the batch
 * stops there. The failed row and everything after it stay put for the next tick, so nothing is lost.
 * If SQS instead rejects that one message ({@link SqsFailures#isPermanentSendFailure}), the row is
 * parked like an unreadable one and the batch carries on. Ids are assigned at insert, not at commit, so two
 * concurrent transactions can commit out of id order. No current event relies on cross-transaction
 * ordering.
 * <p>
 * Runs on its own thread, not Boot's shared one-thread scheduler. Otherwise a send blocked by a
 * SQS outage would stall other {@code @Scheduled} jobs (file-service's trash sweep), and a long
 * sweep would stall event delivery.
 * <p>
 * The row key becomes the FIFO {@code MessageGroupId} (per-key order)
 * and the row id the {@code MessageDeduplicationId}, so a resend within SQS's 5-minute window, e.g.
 * SQS accepted but marking the row SENT didn't commit, is dropped by SQS. Past that window delivery is
 * at-least-once and consumers must tolerate duplicates: notification-service dedupes on {@code eventId},
 * the pending-share claim is idempotent, and a duplicate mail is accepted.
 * <p>
 * {@code FOR UPDATE SKIP LOCKED} lets several instances run this at once without sending a row
 * twice. Each instance takes a different batch, so order holds within an instance's batch, not
 * across instances.
 */
@Slf4j
class OutboxRelay {

    static final int BATCH_SIZE = 100;
    // ponytail: fixed 7 days. Make it a property if a service needs longer (audit) or shorter (volume).
    static final Duration SENT_RETENTION = Duration.ofDays(7);
    // Hibernate's value for SKIP LOCKED (org.hibernate.Timeouts.SKIP_LOCKED_MILLI).
    private static final int SKIP_LOCKED = -2;
    private static final TypeReference<Map<String, String>> TRACE_HEADERS = new TypeReference<>() {};

    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private final SqsOperations sqsOperations;
    private final JsonMapper jsonMapper;
    private final ObservationRegistry observationRegistry;
    private ScheduledExecutorService executor;

    OutboxRelay(EntityManager entityManager, TransactionTemplate transactionTemplate,
                SqsOperations sqsOperations, JsonMapper jsonMapper,
                ObservationRegistry observationRegistry) {
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
        this.sqsOperations = sqsOperations;
        this.jsonMapper = jsonMapper;
        this.observationRegistry = observationRegistry;
    }

    // ponytail: 1s polling, so an event waits up to ~1s. Wake the relay after commit if that's too slow.
    void start() {
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("outbox-relay").factory());
        executor.scheduleWithFixedDelay(() -> {
            try {
                relay();
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
            List<OutboxEventJpaEntity> batch = entityManager
                    .createQuery("select e from OutboxEventJpaEntity e where e.status = :pending order by e.id",
                            OutboxEventJpaEntity.class)
                    .setParameter("pending", OutboxEventStatus.PENDING)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .setHint("jakarta.persistence.lock.timeout", SKIP_LOCKED)
                    .setMaxResults(BATCH_SIZE)
                    .getResultList();

            for (OutboxEventJpaEntity row : batch) {
                Object event;
                try {
                    event = jsonMapper.readValue(row.getPayload(),
                            ClassUtils.forName(row.getPayloadType(), ClassUtils.getDefaultClassLoader()));
                } catch (Exception e) {
                    // Not transient: retrying won't fix it (e.g. the event class was renamed while the
                    // row was waiting). Park the row for a human and keep draining the rest.
                    log.error("Outbox row can't be rebuilt, parking it: id={}, type={}", row.getId(), row.getPayloadType(), e);
                    row.markFailed(e);
                    continue;
                }
                try {
                    send(row, event);
                } catch (Exception e) {
                    if (SqsFailures.isPermanentSendFailure(e)) {
                        // SQS rejected this message itself (queue missing, too large, bad group id):
                        // resending never helps, and stopping here would block every row behind it.
                        log.error("Outbox row rejected by SQS, parking it: id={}, topic={}", row.getId(), row.getTopic(), e);
                        row.markFailed(rootCause(e));
                        continue;
                    }
                    log.warn("Outbox send failed, will retry: id={}, topic={}", row.getId(), row.getTopic(), e);
                    return;
                }
                row.markSent();
            }
        });
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

    private void send(OutboxEventJpaEntity row, Object event) {
        Map<String, String> traceHeaders = row.getTraceHeaders() == null
                ? Map.of() : jsonMapper.readValue(row.getTraceHeaders(), TRACE_HEADERS);
        // Resumes the recording request's trace from the stored headers. It has to be an Observation,
        // not a bare span in scope: SqsTemplate takes the current Observation as its send span's parent,
        // and the first send to a queue finishes on an SDK thread (queue URL lookup) where a thread-local
        // span isn't visible, so that message would start a new trace.
        ReceiverContext<Map<String, String>> context = new ReceiverContext<>(Map::get);
        context.setCarrier(traceHeaders);
        Message<Object> message = MessageBuilder.withPayload(event)
                .setHeader(MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER, groupId(row.getMessageKey()))
                .setHeader(MessageSystemAttributes.SQS_MESSAGE_DEDUPLICATION_ID_HEADER, "outbox-" + row.getId())
                .build();
        Observation.createNotStarted("outbox.relay", () -> context, observationRegistry)
                .contextualName("outbox relay")
                .observe(() -> sqsOperations.send(row.getTopic(), message));
    }

    /** The SDK error SQS returned, not the template/CompletionException wrappers around it. */
    private static Throwable rootCause(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    /** FIFO needs a MessageGroupId, capped at 128 chars, and emails can run to 255: hash those
     * (stable, so the same key still lands in the same group). */
    static String groupId(String key) {
        if (key == null) {
            return "none";
        }
        return key.length() <= 128 ? key : UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
