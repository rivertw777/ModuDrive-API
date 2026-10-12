package com.moduDrive.common.infrastructure.messaging.idempotency;

import java.time.Duration;

/**
 * Remembers which messages a consumer already handled. The queues are standard, so the broker
 * deduplicates nothing. {@link #claim} checks and takes in one step, because two copies of a message
 * can be in flight at once. Keyed by queue name plus deduplication id; records are kept
 * {@link #RETENTION}, as long as a SENT outbox row is kept.
 */
public interface ProcessedEvents {

    Duration RETENTION = Duration.ofDays(7);

    /** {@code false} means the event is already handled or being handled, so the caller skips it. */
    boolean claim(String queue, String deduplicationId);

    /** Gives a claim back after the work failed, so the next delivery gets to try. */
    void release(String queue, String deduplicationId);

    /** Keeps the claim for good: the work succeeded. */
    void markProcessed(String queue, String deduplicationId);
}
