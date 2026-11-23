package com.moduDrive.common.infrastructure.sqs;

import com.moduDrive.common.infrastructure.messaging.MessagePublisher;
import com.moduDrive.common.infrastructure.messaging.PermanentPublishException;
import io.awspring.cloud.sqs.operations.SendBatchOperationFailedException;
import io.awspring.cloud.sqs.operations.SendResult;
import io.awspring.cloud.sqs.operations.SqsOperations;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /** {@code sendMany} isn't observed by Spring Cloud AWS, so the trace headers are set by hand. A failure of
     * the whole call (queue missing, batch over 1 MiB, outage) propagates as is. */
    @Override
    public Map<String, RuntimeException> publishBatch(String queue, List<Outgoing> messages) {
        List<Message<Object>> batch = messages.stream()
                .map(m -> MessageBuilder.withPayload(m.payload())
                        .copyHeaders(m.traceHeaders())
                        .setHeader(SqsAttributes.DEDUPLICATION_ID, m.deduplicationId())
                        .build())
                .toList();
        SendResult.Batch<Object> result;
        try {
            // Read the result too: with SendBatchFailureHandlingStrategy.DO_NOT_THROW failures come back here.
            result = sqsOperations.sendMany(queue, batch);
        } catch (SendBatchOperationFailedException e) {
            // A partial failure; the whole-call one is its parent type and propagates.
            result = e.getSendBatchResult(Object.class);
        }
        Map<String, RuntimeException> failures = new HashMap<>();
        for (SendResult.Failed<Object> failed : result.failed()) {
            String deduplicationId = failed.message().getHeaders().get(SqsAttributes.DEDUPLICATION_ID, String.class);
            if (deduplicationId == null) {
                // Can't tell which row failed; the relay would mark it sent. Failing the call resends one by one.
                throw new IllegalStateException("Batch failure without a deduplication id: " + failed.errorMessage());
            }
            Object code = failed.additionalInformation().get("code");
            RuntimeException cause = new IllegalStateException(code + ": " + failed.errorMessage());
            boolean permanent = SqsFailures.isPermanentBatchEntryFailure(
                    failed.additionalInformation().get("senderFault"), code);
            failures.put(deduplicationId, permanent ? new PermanentPublishException(cause) : cause);
        }
        return failures;
    }
}
