package com.moduDrive.common.infrastructure.sqs;

import com.moduDrive.common.infrastructure.messaging.MessagePublisher;
import com.moduDrive.common.infrastructure.messaging.PermanentPublishException;
import io.awspring.cloud.sqs.operations.SqsOperations;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/** Maps the broker-agnostic publish onto an SQS standard queue: the logical name is the queue name,
 * and the deduplication id travels as a message attribute ({@link SqsAttributes#DEDUPLICATION_ID}). */
class SqsMessagePublisher implements MessagePublisher {

    private final SqsOperations sqsOperations;

    SqsMessagePublisher(SqsOperations sqsOperations) {
        this.sqsOperations = sqsOperations;
    }

    @Override
    public void publish(String queue, String deduplicationId, Object payload) {
        Message<Object> message = MessageBuilder.withPayload(payload)
                .setHeader(SqsAttributes.DEDUPLICATION_ID, deduplicationId)
                .build();
        try {
            sqsOperations.send(queue, message);
        } catch (RuntimeException e) {
            if (SqsFailures.isPermanentSendFailure(e)) {
                throw new PermanentPublishException(e);
            }
            throw e;
        }
    }
}
