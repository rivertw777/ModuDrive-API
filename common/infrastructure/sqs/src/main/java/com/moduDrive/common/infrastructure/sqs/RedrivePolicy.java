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
 * A queue's redrive settings (DLQ and maxReceiveCount), read from the queue itself so they are defined
 * only in {@code .docker/localstack/init-aws.sh} and Terraform. Fields are null when the queue has none.
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

    /** The DLQ this queue redrives to, else the {@code <name>-dlq} convention. */
    String deadLetterQueueOr(String queue) {
        return deadLetterQueue != null ? deadLetterQueue : queue + "-dlq";
    }

    /** Reads each queue's policy once. A failed lookup returns {@link #NONE} and isn't cached. */
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
