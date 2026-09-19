package com.moduDrive.common.infrastructure.sqs;

import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.awspring.cloud.sqs.listener.errorhandler.AsyncErrorHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Listener error handling for every SQS consumer. A permanent failure ({@link SqsFailures}) goes
 * straight to the queue's DLQ with the original body plus a {@code DeadLetterReason} attribute and is
 * deleted from the source queue, so it doesn't burn retries or hold up its FIFO group. Anything else
 * backs off exponentially (visibility 1s, 2s, 4s...) and the queue's redrive policy moves it to the DLQ
 * once maxReceiveCount is used up. If moving to the DLQ itself fails, the message falls back to the
 * retry path rather than being lost.
 */
@Slf4j
class DeadLetteringErrorHandler implements AsyncErrorHandler<Object> {

    static final String REASON_ATTRIBUTE = "DeadLetterReason";
    // SQS allows at most 10 message attributes.
    private static final int MAX_ATTRIBUTES = 10;
    private static final int MAX_REASON_LENGTH = 500;

    private final SqsAsyncClient sqsAsyncClient;
    private final AsyncErrorHandler<Object> retry;

    DeadLetteringErrorHandler(SqsAsyncClient sqsAsyncClient, AsyncErrorHandler<Object> retry) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.retry = retry;
    }

    @Override
    public CompletableFuture<Void> handle(Message<Object> message, Throwable failure) {
        if (!SqsFailures.isPermanentConsumerFailure(failure)) {
            return retry.handle(message, failure);
        }
        return moveToDeadLetterQueue(message, failure).exceptionallyCompose(moveFailure -> {
            log.warn("Couldn't move a permanently failing message to its DLQ, retrying it instead", moveFailure);
            return retry.handle(message, failure);
        });
    }

    private CompletableFuture<Void> moveToDeadLetterQueue(Message<Object> message, Throwable failure) {
        var raw = message.getHeaders().get(SqsHeaders.SQS_SOURCE_DATA_HEADER,
                software.amazon.awssdk.services.sqs.model.Message.class);
        String queue = message.getHeaders().get(SqsHeaders.SQS_QUEUE_NAME_HEADER, String.class);
        if (raw == null || queue == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("No SQS source data on the message"));
        }
        String deadLetterQueue = deadLetterQueueName(queue);
        String reason = reason(failure);
        Map<String, MessageAttributeValue> attributes = new HashMap<>(raw.messageAttributes());
        if (attributes.size() < MAX_ATTRIBUTES) {
            attributes.put(REASON_ATTRIBUTE, MessageAttributeValue.builder().dataType("String").stringValue(reason).build());
        }
        String groupId = raw.attributes().getOrDefault(MessageSystemAttributeName.MESSAGE_GROUP_ID, raw.messageId());

        return sqsAsyncClient.getQueueUrl(r -> r.queueName(deadLetterQueue))
                .thenCompose(url -> sqsAsyncClient.sendMessage(r -> r.queueUrl(url.queueUrl())
                        .messageBody(raw.body())
                        .messageAttributes(attributes)
                        .messageGroupId(groupId)
                        // Same source message moved twice within 5 minutes lands in the DLQ once.
                        .messageDeduplicationId(raw.messageId())))
                .thenAccept(sent -> log.warn("Moved message {} from {} to {} without retrying: {}",
                        raw.messageId(), queue, deadLetterQueue, reason));
    }

    /** {@code name.fifo} → {@code name-dlq.fifo}; the convention every queue follows. */
    static String deadLetterQueueName(String queue) {
        return queue.endsWith(".fifo")
                ? queue.substring(0, queue.length() - ".fifo".length()) + "-dlq.fifo"
                : queue + "-dlq";
    }

    private static String reason(Throwable failure) {
        Throwable cause = SqsFailures.findCause(failure, c -> SqsFailures.isPermanentConsumerFailure(c)
                && (c.getCause() == null || !SqsFailures.isPermanentConsumerFailure(c.getCause())));
        Throwable shown = cause != null ? cause : failure;
        String reason = shown.getClass().getName() + ": " + shown.getMessage();
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }
}
