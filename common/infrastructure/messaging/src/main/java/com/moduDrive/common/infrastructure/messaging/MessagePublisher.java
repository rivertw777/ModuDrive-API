package com.moduDrive.common.infrastructure.messaging;

/** Sends one message to the broker: the only broker-specific step of the outbox
 * (implemented by {@code common:infrastructure:sqs}). */
public interface MessagePublisher {

    /**
     * @param queue           queue name as {@code common:event} declares it
     * @param deduplicationId same value for every resend of one event, so the consumer can tell a
     *                        resend from a new event
     * @throws PermanentPublishException when the broker rejects this message itself, so resending can
     *                                   never work. Anything else counts as transient
     */
    void publish(String queue, String deduplicationId, Object payload);
}
