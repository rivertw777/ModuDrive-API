package com.moduDrive.common.infrastructure.messaging.idempotency;

/**
 * Remembers which messages a consumer already handled, so a redelivery doesn't repeat its effect
 * (a second invite mail, a second notification row). The queues are standard, so the broker
 * deduplicates nothing: this is the only thing standing between a redelivery and a repeat.
 * <p>
 * {@link #claim} takes the event and says whether it got it, in one step. Asking first and writing
 * later would leave a gap: on a standard queue two copies of the same message can be in flight at
 * once, and both would find nothing recorded.
 * <p>
 * The key is the queue name plus the deduplication id, which stays the same for one event no matter
 * how often it's resent. Records are kept {@link #RETENTION} — as long as a producer's outbox row
 * can live — and then dropped.
 */
public interface ProcessedEvents {

    java.time.Duration RETENTION = java.time.Duration.ofDays(7);

    /** Takes this event for the caller. {@code false} means someone else has it — handled already, or
     * being handled right now — so the caller skips it. */
    boolean claim(String queue, String deduplicationId);

    /** Gives a claim back after the work failed, so the next delivery gets to try. */
    void release(String queue, String deduplicationId);

    /** Keeps the claim for good: the work succeeded. */
    void markProcessed(String queue, String deduplicationId);
}
