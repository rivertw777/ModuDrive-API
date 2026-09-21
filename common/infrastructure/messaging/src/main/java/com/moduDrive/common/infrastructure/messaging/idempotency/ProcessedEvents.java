package com.moduDrive.common.infrastructure.messaging.idempotency;

/**
 * Remembers which messages a consumer already handled, so a redelivery doesn't repeat its effect
 * (a second invite mail, a second notification row). The queues are standard, so the broker
 * deduplicates nothing: this is the only thing standing between a redelivery and a repeat.
 * <p>
 * The key is the queue name plus that same deduplication id, which stays the same for one event no
 * matter how often it's resent. Records are kept {@link #RETENTION} — as long as a producer's outbox
 * row can live — and then dropped.
 */
public interface ProcessedEvents {

    java.time.Duration RETENTION = java.time.Duration.ofDays(7);

    boolean isProcessed(String queue, String deduplicationId);

    void markProcessed(String queue, String deduplicationId);
}
