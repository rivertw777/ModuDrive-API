package com.moduDrive.common.infrastructure.sqs;

import com.moduDrive.common.infrastructure.messaging.MessagePublisher;
import com.moduDrive.common.infrastructure.messaging.PermanentPublishException;
import io.awspring.cloud.sqs.listener.SqsHeaders.MessageSystemAttributes;
import io.awspring.cloud.sqs.operations.SqsOperations;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Maps the broker-agnostic publish onto SQS FIFO: the destination gets the queue's {@code .fifo}
 * suffix, the ordering key becomes the {@code MessageGroupId} and the deduplication id the
 * {@code MessageDeduplicationId}. */
class SqsMessagePublisher implements MessagePublisher {

    // SQS caps a group id at 128 characters; emails can run to 255.
    private static final int MAX_GROUP_ID_LENGTH = 128;

    private final SqsOperations sqsOperations;

    SqsMessagePublisher(SqsOperations sqsOperations) {
        this.sqsOperations = sqsOperations;
    }

    @Override
    public void publish(String destination, String orderingKey, String deduplicationId, Object payload) {
        Message<Object> message = MessageBuilder.withPayload(payload)
                .setHeader(MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER, groupId(orderingKey))
                .setHeader(MessageSystemAttributes.SQS_MESSAGE_DEDUPLICATION_ID_HEADER, deduplicationId)
                .build();
        try {
            sqsOperations.send(SqsQueues.queueName(destination), message);
        } catch (RuntimeException e) {
            if (SqsFailures.isPermanentSendFailure(e)) {
                throw new PermanentPublishException(e);
            }
            throw e;
        }
    }

    /** A FIFO queue needs a group id, so a null key still gets one; an over-long key is hashed
     * (stable, so the same key keeps landing in the same group). */
    static String groupId(String orderingKey) {
        if (orderingKey == null) {
            return "none";
        }
        return orderingKey.length() <= MAX_GROUP_ID_LENGTH
                ? orderingKey
                : UUID.nameUUIDFromBytes(orderingKey.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
