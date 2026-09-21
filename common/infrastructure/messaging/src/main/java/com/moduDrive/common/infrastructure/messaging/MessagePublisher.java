package com.moduDrive.common.infrastructure.messaging;

/**
 * Sends one message to the broker. The only broker-specific step in the outbox, so it's the seam an
 * adapter plugs into ({@code common:infrastructure:sqs} today).
 */
public interface MessagePublisher {

    /**
     * @param queue           logical name of the queue this message is for, without any naming
     *                        rule the broker adds of its own (the adapter applies that)
     * @param deduplicationId same value for the same event however often it's resent, so the consumer
     *                        can tell a resend from a new event
     * @throws PermanentPublishException when the broker rejects this message itself, so resending it
     *                                   can never work. Anything else counts as transient
     */
    void publish(String queue, String deduplicationId, Object payload);
}
