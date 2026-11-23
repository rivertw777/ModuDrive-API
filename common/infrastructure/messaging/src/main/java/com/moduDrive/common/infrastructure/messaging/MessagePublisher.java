package com.moduDrive.common.infrastructure.messaging;

import java.util.List;
import java.util.Map;

/** Sends messages to the broker: the only broker-specific step of the outbox
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

    /**
     * Sends up to 10 messages (SQS's batch limit) to one queue in one broker call. Each message carries its
     * own trace headers, since there is no single current trace for the call.
     *
     * @return failures keyed by deduplication id, a {@link PermanentPublishException} when resending that
     *         message can't help; every message not in it was sent
     * @throws RuntimeException when the call as a whole failed, so none of them is known sent
     */
    Map<String, RuntimeException> publishBatch(String queue, List<Outgoing> messages);

    /** @param traceHeaders W3C trace context of the request that recorded the event; sent as headers */
    record Outgoing(String deduplicationId, Object payload, Map<String, String> traceHeaders) {
    }
}
