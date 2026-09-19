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
 * Each service has its own database, so each has its own table. */
@Getter
@Entity
@Table(name = "outbox_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class OutboxEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
    @Column(length = 65_535)
    private String traceHeaders;

    @Column(nullable = false)
    private Instant createdAt;

    /** Set when the payload can't be turned back into its event class. The relay skips these rows
     * from then on, so a pile of them can't fill every batch and block the rows behind them. They
     * stay in the table for a human to look at. */
    private Instant failedAt;

    OutboxEventJpaEntity(String topic, String messageKey, String payloadType, String payload,
                         String traceHeaders) {
        this.topic = topic;
        this.messageKey = messageKey;
        this.payloadType = payloadType;
        this.payload = payload;
        this.traceHeaders = traceHeaders;
        this.createdAt = Instant.now();
    }

    void markFailed() {
        this.failedAt = Instant.now();
    }
}
