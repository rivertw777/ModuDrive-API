package com.moduDrive.common.infrastructure.sqs;

import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.awspring.cloud.sqs.listener.SqsHeaders.MessageSystemAttributes;
import io.awspring.cloud.sqs.listener.errorhandler.AsyncErrorHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Listener error handling for every SQS consumer. The message goes to the queue's DLQ with the
 * original body plus a {@code DeadLetterReason} attribute, and is deleted from the source queue, when
 * <ul>
 *   <li>the failure is permanent ({@link SqsFailures}): no retries, so it doesn't hold up its FIFO group;</li>
 *   <li>it's the last receive the queue's redrive policy allows: retries are used up, so move it now
 *       with the reason instead of letting SQS redrive it on the next receive without one.</li>
 * </ul>
 * Anything else backs off exponentially (visibility 1s, 2s, 4s...). The DLQ and the receive limit come
 * from the queue's own {@code RedrivePolicy}, so they're set in one place (elasticmq.conf / Terraform).
 * The redrive policy still catches what never reaches this handler (a crashed consumer, a body that
 * isn't JSON). If moving to the DLQ fails, the message falls back to the retry path rather than being lost.
 */
@Slf4j
class DeadLetteringErrorHandler implements AsyncErrorHandler<Object> {

    static final String REASON_ATTRIBUTE = "DeadLetterReason";
    // SQS allows at most 10 message attributes.
    private static final int MAX_ATTRIBUTES = 10;
    private static final int MAX_REASON_LENGTH = 500;
    private static final Pattern MAX_RECEIVE_COUNT = Pattern.compile("\"maxReceiveCount\"\\s*:\\s*\"?(\\d+)");
    private static final Pattern DEAD_LETTER_ARN = Pattern.compile("\"deadLetterTargetArn\"\\s*:\\s*\"([^\"]+)\"");

    private final SqsAsyncClient sqsAsyncClient;
    private final AsyncErrorHandler<Object> retry;
    private final Map<String, CompletableFuture<RedrivePolicy>> redrivePolicies = new ConcurrentHashMap<>();

    DeadLetteringErrorHandler(SqsAsyncClient sqsAsyncClient, AsyncErrorHandler<Object> retry) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.retry = retry;
    }

    /** The queue's redrive settings; null fields when the queue has no redrive policy. */
    record RedrivePolicy(Integer maxReceiveCount, String deadLetterQueue) {

        static final RedrivePolicy NONE = new RedrivePolicy(null, null);

        static RedrivePolicy parse(String json) {
            if (json == null) {
                return NONE;
            }
            Matcher count = MAX_RECEIVE_COUNT.matcher(json);
            Matcher arn = DEAD_LETTER_ARN.matcher(json);
            return new RedrivePolicy(count.find() ? Integer.valueOf(count.group(1)) : null,
                    arn.find() ? arn.group(1).substring(arn.group(1).lastIndexOf(':') + 1) : null);
        }
    }

    @Override
    public CompletableFuture<Void> handle(Message<Object> message, Throwable failure) {
        String queueUrl = message.getHeaders().get(SqsHeaders.SQS_QUEUE_URL_HEADER, String.class);
        boolean permanent = SqsFailures.isPermanentConsumerFailure(failure);
        return redrivePolicy(queueUrl).thenCompose(policy -> {
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

    /** Cached per queue. A failed lookup isn't cached and counts as "no policy" this time: permanent
     * failures still move (by the naming convention), retryable ones just retry. */
    private CompletableFuture<RedrivePolicy> redrivePolicy(String queueUrl) {
        if (queueUrl == null) {
            return CompletableFuture.completedFuture(RedrivePolicy.NONE);
        }
        CompletableFuture<RedrivePolicy> policy = redrivePolicies.computeIfAbsent(queueUrl, url -> sqsAsyncClient
                .getQueueAttributes(r -> r.queueUrl(url).attributeNames(QueueAttributeName.REDRIVE_POLICY))
                .thenApply(response -> RedrivePolicy.parse(response.attributes().get(QueueAttributeName.REDRIVE_POLICY))));
        return policy.exceptionally(lookupFailure -> {
            redrivePolicies.remove(queueUrl, policy);
            log.warn("Couldn't read the redrive policy of {}", queueUrl, lookupFailure);
            return RedrivePolicy.NONE;
        });
    }

    private CompletableFuture<Void> moveToDeadLetterQueue(Message<Object> message, RedrivePolicy policy, String reason) {
        var raw = message.getHeaders().get(SqsHeaders.SQS_SOURCE_DATA_HEADER,
                software.amazon.awssdk.services.sqs.model.Message.class);
        String queue = message.getHeaders().get(SqsHeaders.SQS_QUEUE_NAME_HEADER, String.class);
        if (raw == null || queue == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("No SQS source data on the message"));
        }
        String deadLetterQueue = policy.deadLetterQueue() != null ? policy.deadLetterQueue() : deadLetterQueueName(queue);
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
                .thenAccept(sent -> log.warn("Moved message {} from {} to {}: {}",
                        raw.messageId(), queue, deadLetterQueue, reason));
    }

    private static long receiveCount(Message<Object> message) {
        Object count = message.getHeaders().get(MessageSystemAttributes.SQS_APPROXIMATE_RECEIVE_COUNT);
        return count == null ? 0 : Long.parseLong(count.toString());
    }

    /** Fallback when the queue has no redrive policy: {@code name.fifo} → {@code name-dlq.fifo}. */
    static String deadLetterQueueName(String queue) {
        return queue.endsWith(".fifo")
                ? queue.substring(0, queue.length() - ".fifo".length()) + "-dlq.fifo"
                : queue + "-dlq";
    }

    /** The innermost permanent cause for a permanent failure, else the root cause: what actually went wrong,
     * not the listener-invocation wrapper around it. */
    static String reason(Throwable failure) {
        Throwable shown = SqsFailures.findCause(failure, c -> SqsFailures.isPermanentConsumerFailure(c)
                && (c.getCause() == null || !SqsFailures.isPermanentConsumerFailure(c.getCause())));
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
