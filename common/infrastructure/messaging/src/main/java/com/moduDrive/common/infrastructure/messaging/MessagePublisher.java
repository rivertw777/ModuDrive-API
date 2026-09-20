package com.moduDrive.common.infrastructure.messaging;

/**
 * Sends one message to the broker. The only broker-specific step in the outbox, so it's the seam an
 * adapter plugs into ({@code common:infrastructure:sqs} today).
 */
public interface MessagePublisher {

    /**
     * @param destination     the queue/topic name
     * @param orderingKey     messages sharing a key are delivered in order; different keys may go in
     *                        parallel. Null means order doesn't matter
     * @param deduplicationId same value for the same event however often it's resent, so a broker that
     *                        deduplicates can drop the repeat
     * @throws PermanentPublishException when the broker rejects this message itself, so resending it
     *                                   can never work. Anything else counts as transient
     */
    void publish(String destination, String orderingKey, String deduplicationId, Object payload);
}
