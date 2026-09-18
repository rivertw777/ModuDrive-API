package com.moduDrive.common.infrastructure.outbox;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ClassUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Sends {@code outbox_event} rows to Kafka in insertion order and deletes each one after the
 * broker acks it. If a send fails (broker down), the batch stops there. The failed row and
 * everything after it stay put for the next tick, so nothing is lost and order is kept.
 * <p>
 * Delivery is at-least-once. If the broker acks but the delete doesn't commit, the row is sent
 * again. Consumers must tolerate duplicates: notification-service dedupes on {@code eventId},
 * the pending-share claim is idempotent, and a duplicate mail is accepted.
 * <p>
 * {@code FOR UPDATE SKIP LOCKED} lets several instances run this at once without sending a row
 * twice. Each instance takes a different batch, so order holds within an instance's batch, not
 * across instances.
 */
@Slf4j
class OutboxRelay {

    static final int BATCH_SIZE = 100;
    private static final long SEND_TIMEOUT_SECONDS = 10;
    // Hibernate's value for SKIP LOCKED (org.hibernate.Timeouts.SKIP_LOCKED_MILLI).
    private static final int SKIP_LOCKED = -2;
    private static final TypeReference<Map<String, String>> TRACE_HEADERS = new TypeReference<>() {};

    private final String source;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final Tracer tracer;
    private final Propagator propagator;

    OutboxRelay(String source, EntityManager entityManager, TransactionTemplate transactionTemplate,
                KafkaTemplate<Object, Object> kafkaTemplate, JsonMapper jsonMapper,
                Tracer tracer, Propagator propagator) {
        this.source = source;
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.tracer = tracer;
        this.propagator = propagator;
    }

    // ponytail: 1s polling, so an event waits up to ~1s. Wake the relay after commit if that's too slow.
    @Scheduled(fixedDelay = 1_000)
    void relay() {
        transactionTemplate.executeWithoutResult(status -> {
            List<OutboxEventJpaEntity> batch = entityManager
                    .createQuery("select e from OutboxEventJpaEntity e where e.source = :source order by e.id",
                            OutboxEventJpaEntity.class)
                    .setParameter("source", source)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .setHint("jakarta.persistence.lock.timeout", SKIP_LOCKED)
                    .setMaxResults(BATCH_SIZE)
                    .getResultList();

            for (OutboxEventJpaEntity row : batch) {
                Object event;
                try {
                    event = jsonMapper.readValue(row.getPayload(),
                            ClassUtils.forName(row.getPayloadType(), OutboxRelay.class.getClassLoader()));
                } catch (Exception e) {
                    // Not transient: retrying won't fix it (e.g. the event class was renamed while the
                    // row was waiting). Leave the row for a human and keep draining the rest.
                    log.error("Outbox row can't be rebuilt, skipping: id={}, type={}", row.getId(), row.getPayloadType(), e);
                    continue;
                }
                try {
                    send(row, event);
                } catch (Exception e) {
                    log.warn("Outbox send failed, will retry: id={}, topic={}", row.getId(), row.getTopic(), e);
                    return;
                }
                entityManager.remove(row);
            }
        });
    }

    private void send(OutboxEventJpaEntity row, Object event) throws Exception {
        Map<String, String> traceHeaders = row.getTraceHeaders() == null
                ? Map.of() : jsonMapper.readValue(row.getTraceHeaders(), TRACE_HEADERS);
        Span span = propagator.extract(traceHeaders, Map::get).name("outbox relay").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            kafkaTemplate.send(row.getTopic(), row.getMessageKey(), event).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
