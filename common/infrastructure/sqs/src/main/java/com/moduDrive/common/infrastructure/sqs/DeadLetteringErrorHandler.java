package com.moduDrive.common.infrastructure.sqs;

import com.moduDrive.common.infrastructure.messaging.PermanentFailures;
import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.awspring.cloud.sqs.listener.SqsHeaders.MessageSystemAttributes;
import io.awspring.cloud.sqs.listener.errorhandler.AsyncErrorHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Listener error handling for every SQS consumer. The message goes to the queue's DLQ with the
 * original body plus a {@code DeadLetterReason} attribute, and is deleted from the source queue, when
 * <ul>
 *   <li>the failure is permanent ({@link PermanentFailures}): retrying can't fix it, so it moves now
 *       instead of burning the queue's receive budget;</li>
 *   <li>it's the last receive the queue's redrive policy allows: retries are used up, so move it now
 *       with the reason instead of letting SQS redrive it on the next receive without one.</li>
 * </ul>
 * Anything else backs off exponentially (visibility 1s, 2s, 4s...). The DLQ and the receive limit come
 * from the queue's own {@link RedrivePolicy}, so they're set in one place (init-aws.sh / Terraform).
 * The redrive policy still catches what never reaches this handler (a crashed consumer, a body that
 * isn't JSON). If moving to the DLQ fails, the message falls back to the retry path rather than being lost.
 * <p>
 * Nothing here tells anyone a message was parked — that's {@link DeadLetterQueueMetrics}' job.
 */
@Slf4j
class DeadLetteringErrorHandler implements AsyncErrorHandler<Object> {

    static final String REASON_ATTRIBUTE = "DeadLetterReason";
    // SQS allows at most 10 message attributes.
    private static final int MAX_ATTRIBUTES = 10;
    private static final int MAX_REASON_LENGTH = 500;

    private final SqsAsyncClient sqsAsyncClient;
    private final AsyncErrorHandler<Object> retry;
    private final RedrivePolicy.Cache redrivePolicies;

    DeadLetteringErrorHandler(SqsAsyncClient sqsAsyncClient, AsyncErrorHandler<Object> retry) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.retry = retry;
        this.redrivePolicies = new RedrivePolicy.Cache(sqsAsyncClient);
    }

    @Override
    public CompletableFuture<Void> handle(Message<Object> message, Throwable failure) {
        String queueUrl = message.getHeaders().get(SqsHeaders.SQS_QUEUE_URL_HEADER, String.class);
        boolean permanent = PermanentFailures.isPermanentForConsumer(failure);
        return redrivePolicies.get(queueUrl).thenCompose(policy -> {
            long receiveCount = receiveCount(message);
            boolean lastAttempt = policy.maxReceiveCount() != null && receiveCount >= policy.maxReceiveCount();
            if (!permanent && !lastAttempt) {
                return retry.handle(message, failure);
            }
            String reason = permanent
                    ? reason(failure)
                    : "Retries exhausted after " + receiveCount + " attempts: " + reason(failure);
            return moveToDeadLetterQueue(message, policy, reason).exceptionallyCompose(moveFailure -> {
                log.warn("Couldn't move a failed message to its DLQ, retrying it instead", moveFailure);
                return retry.handle(message, failure);
            });
        });
    }

    private CompletableFuture<Void> moveToDeadLetterQueue(Message<Object> message, RedrivePolicy policy, String reason) {
        var raw = message.getHeaders().get(SqsHeaders.SQS_SOURCE_DATA_HEADER,
                software.amazon.awssdk.services.sqs.model.Message.class);
        String queue = message.getHeaders().get(SqsHeaders.SQS_QUEUE_NAME_HEADER, String.class);
        if (raw == null || queue == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("No SQS source data on the message"));
        }
        String deadLetterQueue = policy.deadLetterQueueOr(queue);
        Map<String, MessageAttributeValue> attributes = new HashMap<>(raw.messageAttributes());
        if (attributes.size() < MAX_ATTRIBUTES) {
            attributes.put(REASON_ATTRIBUTE, MessageAttributeValue.builder().dataType("String").stringValue(reason).build());
        }
        return sqsAsyncClient.getQueueUrl(r -> r.queueName(deadLetterQueue))
                .thenCompose(url -> sqsAsyncClient.sendMessage(r -> r.queueUrl(url.queueUrl())
                        .messageBody(raw.body())
                        .messageAttributes(attributes)))
                .thenAccept(sent -> log.warn("Moved message {} from {} to {}: {}",
                        raw.messageId(), queue, deadLetterQueue, reason));
    }

    private static long receiveCount(Message<Object> message) {
        Object count = message.getHeaders().get(MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT);
        return count == null ? 0 : Long.parseLong(count.toString());
    }

    /** The innermost permanent cause for a permanent failure, else the root cause: what actually went wrong,
     * not the listener-invocation wrapper around it. */
    static String reason(Throwable failure) {
        Throwable shown = PermanentFailures.findCause(failure, c -> PermanentFailures.isPermanentForConsumer(c)
                && (c.getCause() == null || !PermanentFailures.isPermanentForConsumer(c.getCause())));
        if (shown == null) {
            shown = failure;
            while (shown.getCause() != null && shown.getCause() != shown) {
                shown = shown.getCause();
            }
        }
        String reason = shown.getClass().getName() + ": " + shown.getMessage();
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }
}
