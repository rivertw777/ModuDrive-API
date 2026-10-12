package com.moduDrive.common.infrastructure.messaging.outbox;

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

/** One message, from recording to delivery ({@link OutboxEventStatus}). SENT rows are purged after
 * {@link OutboxRelay#SENT_RETENTION}; FAILED rows need a human. */
@Getter
@Entity
@Table(name = "outbox_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class OutboxEventJpaEntity {

    /** Must match the {@code failure_reason} column in each service's migration. */
    static final int FAILURE_REASON_COLUMN_LENGTH = 1000;
    /** Enough for the simple class name and message the alert shows; the full error is in the logs. */
    static final int FAILURE_REASON_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Queue name as {@code common:event} declares it. */
    @Column(nullable = false)
    private String queue;

    /** Fully-qualified event class, so the relay can rebuild the object. */
    @Column(nullable = false)
    private String payloadType;

    @Column(nullable = false, length = 65_535)
    private String payload;

    /** W3C trace headers of the recording request, as JSON, so the consumer's spans join that trace. */
    @Column(length = 65_535)
    private String traceHeaders;

    @Column(nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxEventStatus status;

    private Instant sentAt;

    /** Set with FAILED: the payload can't be rebuilt, or the broker rejected the message. */
    private Instant failedAt;

    /** Why it became FAILED: exception class and message, cut to {@link #FAILURE_REASON_LENGTH}. */
    @Column(length = FAILURE_REASON_COLUMN_LENGTH)
    private String failureReason;

    OutboxEventJpaEntity(String queue, String payloadType, String payload,
                         String traceHeaders) {
        this.queue = queue;
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
        String reason = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        this.failureReason = reason.length() <= FAILURE_REASON_LENGTH ? reason : reason.substring(0, FAILURE_REASON_LENGTH);
    }
}
