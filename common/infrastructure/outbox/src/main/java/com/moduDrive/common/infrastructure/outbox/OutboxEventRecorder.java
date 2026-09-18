package com.moduDrive.common.infrastructure.outbox;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Stands in for {@code KafkaTemplate.send}: saves the event to {@code outbox_event} instead, and
 * {@link OutboxRelay} sends it to Kafka later. The write joins the caller's transaction, so the
 * event commits or rolls back with the business change. If there is no transaction, it opens its
 * own. Either way, once this returns without an exception the event will reach Kafka even if the
 * broker is down right now.
 */
public class OutboxEventRecorder {

    private final String source;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private final JsonMapper jsonMapper;
    private final Tracer tracer;
    private final Propagator propagator;

    OutboxEventRecorder(String source, EntityManager entityManager, TransactionTemplate transactionTemplate,
                        JsonMapper jsonMapper, Tracer tracer, Propagator propagator) {
        this.source = source;
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
        this.jsonMapper = jsonMapper;
        this.tracer = tracer;
        this.propagator = propagator;
    }

    public void record(String topic, String key, Object event) {
        Map<String, String> traceHeaders = new HashMap<>();
        Span span = tracer.currentSpan();
        if (span != null) {
            propagator.inject(span.context(), traceHeaders, Map::put);
        }
        OutboxEventJpaEntity row = new OutboxEventJpaEntity(source, topic, key, event.getClass().getName(),
                jsonMapper.writeValueAsString(event), jsonMapper.writeValueAsString(traceHeaders));
        transactionTemplate.executeWithoutResult(status -> entityManager.persist(row));
    }
}
