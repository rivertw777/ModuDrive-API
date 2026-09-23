package com.moduDrive.common.infrastructure.sqs;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A queue's redrive settings, read from the queue itself: which DLQ its failures go to and how many
 * receives it allows before SQS moves a message there. The app never writes either down — they're set
 * once in {@code .docker/localstack/init-aws.sh} and Terraform, and both
 * {@link DeadLetteringErrorHandler} (to move a message with a reason) and {@link DeadLetterQueueMetrics}
 * (to watch the DLQ) ask the queue.
 * <p>
 * Fields are null when the queue has no redrive policy at all.
 */
@Slf4j
record RedrivePolicy(Integer maxReceiveCount, String deadLetterQueue) {

    static final RedrivePolicy NONE = new RedrivePolicy(null, null);

    private static final Pattern MAX_RECEIVE_COUNT = Pattern.compile("\"maxReceiveCount\"\\s*:\\s*\"?(\\d+)");
    private static final Pattern DEAD_LETTER_ARN = Pattern.compile("\"deadLetterTargetArn\"\\s*:\\s*\"([^\"]+)\"");

    static RedrivePolicy parse(String json) {
        if (json == null) {
            return NONE;
        }
        Matcher count = MAX_RECEIVE_COUNT.matcher(json);
        Matcher arn = DEAD_LETTER_ARN.matcher(json);
        return new RedrivePolicy(count.find() ? Integer.valueOf(count.group(1)) : null,
                arn.find() ? arn.group(1).substring(arn.group(1).lastIndexOf(':') + 1) : null);
    }

    /** The DLQ this queue redrives to, falling back to the {@code <name>-dlq} convention both our
     * queue definitions follow when the queue has no policy (or it couldn't be read). */
    String deadLetterQueueOr(String queue) {
        return deadLetterQueue != null ? deadLetterQueue : queue + "-dlq";
    }

    /**
     * Reads the policy of every queue asked for, at most once each. A failed lookup isn't cached and
     * counts as {@link #NONE} this time, so the next call tries again.
     */
    static final class Cache {

        private final SqsAsyncClient sqsAsyncClient;
        private final Map<String, CompletableFuture<RedrivePolicy>> byQueueUrl = new ConcurrentHashMap<>();

        Cache(SqsAsyncClient sqsAsyncClient) {
            this.sqsAsyncClient = sqsAsyncClient;
        }

        CompletableFuture<RedrivePolicy> get(String queueUrl) {
            if (queueUrl == null) {
                return CompletableFuture.completedFuture(NONE);
            }
            CompletableFuture<RedrivePolicy> policy = byQueueUrl.computeIfAbsent(queueUrl, url -> sqsAsyncClient
                    .getQueueAttributes(r -> r.queueUrl(url).attributeNames(QueueAttributeName.REDRIVE_POLICY))
                    .thenApply(response -> parse(response.attributes().get(QueueAttributeName.REDRIVE_POLICY))));
            return policy.exceptionally(lookupFailure -> {
                byQueueUrl.remove(queueUrl, policy);
                log.warn("Couldn't read the redrive policy of {}", queueUrl, lookupFailure);
                return NONE;
            });
        }
    }
}
