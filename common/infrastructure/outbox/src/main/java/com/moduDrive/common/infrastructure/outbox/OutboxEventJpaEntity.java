package com.moduDrive.common.infrastructure.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One Kafka record waiting to be sent. The row lives only until {@link OutboxRelay} has sent it,
 * so a non-empty table means the broker is behind. The oldest {@code created_at} shows how far.
 * <p>
 * Every service shares one database here, so they share this table too. {@code source} (the
 * recording service's {@code spring.application.name}) keeps each relay on its own rows. */
@Getter
@Entity
@Table(name = "outbox_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class OutboxEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private String topic;

    @Column(name = "message_key")
    private String messageKey;

    /** Fully-qualified class of the event record, so the relay can rebuild the exact object and
     * send it through the normal {@code JsonSerializer} (same {@code __TypeId__} header as before). */
    @Column(nullable = false)
    private String payloadType;

    @Column(nullable = false, length = 65_535)
    private String payload;

    /** W3C trace headers of the request that recorded the event, as JSON. The relay picks them up
     * again so the consumer's spans still join the original trace in Tempo. */
    @Column(length = 1_024)
    private String traceHeaders;

    @Column(nullable = false)
    private Instant createdAt;

    OutboxEventJpaEntity(String source, String topic, String messageKey, String payloadType, String payload,
                         String traceHeaders) {
        this.source = source;
        this.topic = topic;
        this.messageKey = messageKey;
        this.payloadType = payloadType;
        this.payload = payload;
        this.traceHeaders = traceHeaders;
        this.createdAt = Instant.now();
    }
}
