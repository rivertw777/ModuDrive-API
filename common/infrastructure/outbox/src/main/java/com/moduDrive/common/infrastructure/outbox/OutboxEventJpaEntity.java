package com.moduDrive.common.infrastructure.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One SQS message, from recording to delivery ({@link OutboxEventStatus}). Sent rows stay for
 * {@link OutboxRelay#SENT_RETENTION} as an audit trail and possible resend source, then get purged.
 * Old PENDING rows mean sending is behind (the oldest {@code created_at} shows how far); FAILED rows
 * need a human. Each service has its own database, so each has its own table. */
@Getter
@Entity
@Table(name = "outbox_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class OutboxEventJpaEntity {

    static final int FAILURE_REASON_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The destination queue name (the column predates the move from Kafka topics to SQS). */
    @Column(nullable = false)
    private String topic;

    @Column(name = "message_key")
    private String messageKey;

    /** Fully-qualified class of the event record, so the relay can rebuild the exact object and
     * send it through {@code SqsTemplate}'s normal JSON conversion. */
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxEventStatus status;

    private Instant sentAt;

    /** Set with FAILED: the payload can't be turned back into its event class, or SQS rejected the
     * message itself. The relay only picks up PENDING rows, so a pile of these can't fill every batch
     * and block the rows behind them. */
    private Instant failedAt;

    /** Why it became FAILED: exception class and message, cut to the column size. */
    @Column(length = FAILURE_REASON_LENGTH)
    private String failureReason;

    OutboxEventJpaEntity(String topic, String messageKey, String payloadType, String payload,
                         String traceHeaders) {
        this.topic = topic;
        this.messageKey = messageKey;
        this.payloadType = payloadType;
        this.payload = payload;
        this.traceHeaders = traceHeaders;
        this.createdAt = Instant.now();
        this.status = OutboxEventStatus.PENDING;
    }

    void markSent() {
        this.status = OutboxEventStatus.SENT;
        this.sentAt = Instant.now();
    }

    void markFailed(Throwable cause) {
        this.status = OutboxEventStatus.FAILED;
        this.failedAt = Instant.now();
        String reason = cause.getClass().getName() + ": " + cause.getMessage();
        this.failureReason = reason.length() <= FAILURE_REASON_LENGTH ? reason : reason.substring(0, FAILURE_REASON_LENGTH);
    }
}
