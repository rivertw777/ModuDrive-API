package com.moduDrive.common.infrastructure.sqs;

import io.awspring.cloud.sqs.listener.AbstractMessageListenerContainer;
import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Publishes how many messages are sitting in the DLQ of each queue this service consumes, so an alert
 * can say that a consumer gave up on something.
 * <p>
 * Nothing else notices. {@link DeadLetteringErrorHandler} moves a message and logs a line, the source
 * queue goes back to looking empty, and the message then waits in the DLQ for a human to read its
 * {@code DeadLetterReason}, fix the cause and redrive it — until SQS's retention (4 days by default)
 * throws it away. The count is 0 or a problem; there is no healthy non-zero value.
 * <p>
 * Published per source queue rather than per DLQ, so the label matches every other messaging metric
 * and alert. The DLQ itself is whatever the source queue's {@link RedrivePolicy} points at, resolved
 * once per queue: a service consuming nothing publishes nothing and makes no calls.
 */
// ponytail: polls GetQueueAttributes, because the depth isn't something the consumer can observe —
// it never sees the DLQ. On AWS a CloudWatch alarm on ApproximateNumberOfMessagesVisible would do the
// same without the app, but the alerting pipeline is Prometheus for now, and it has to work locally too.
@Slf4j
class DeadLetterQueueMetrics {

    static final String METER_NAME = "modudrive.dlq.messages";
    // The alert evaluates every minute; anything finer just spends API calls.
    private static final int INTERVAL_SECONDS = 30;

    private final SqsAsyncClient sqsAsyncClient;
    private final MessageListenerContainerRegistry containers;
    private final MeterRegistry meterRegistry;
    private final RedrivePolicy.Cache redrivePolicies;
    private final Map<String, String> deadLetterQueueUrls = new ConcurrentHashMap<>();
    private MultiGauge messages;
    private ScheduledExecutorService executor;

    DeadLetterQueueMetrics(SqsAsyncClient sqsAsyncClient, MessageListenerContainerRegistry containers,
                           MeterRegistry meterRegistry) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.containers = containers;
        this.meterRegistry = meterRegistry;
        this.redrivePolicies = new RedrivePolicy.Cache(sqsAsyncClient);
    }

    void start() {
        if (meterRegistry == null) {
            return; // No registry (a test context): nothing would be published, so don't poll either.
        }
        messages = MultiGauge.builder(METER_NAME)
                .description("Messages parked in a consumed queue's dead letter queue")
                .register(meterRegistry);
        // Own thread rather than Boot's shared scheduler: a blocked SQS call would stall whatever
        // else is scheduled, and this one blocks on purpose (see refresh).
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("dlq-metrics").factory());
        // The first tick waits an interval: listener containers register during context refresh, so
        // right now this service may not know yet which queues it consumes.
        executor.scheduleWithFixedDelay(() -> {
            try {
                refresh();
            } catch (Exception e) {
                // An escaped exception would cancel the schedule for good.
                log.error("DLQ metrics tick failed", e);
            }
        }, INTERVAL_SECONDS, INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /**
     * Republishes one series per consumed queue, including the zeroes — unlike the outbox gauges, the
     * set of queues is fixed at startup, so a series that stays at 0 is the normal state and also shows
     * the poll is alive. A queue whose DLQ can't be read is skipped rather than reported as 0.
     */
    void refresh() {
        List<MultiGauge.Row<?>> rows = new ArrayList<>();
        for (String queue : consumedQueues()) {
            try {
                rows.add(MultiGauge.Row.of(Tags.of("queue", queue), deadLetterCount(queue)));
            } catch (Exception e) {
                log.warn("Couldn't read the DLQ of {}", queue, e);
            }
        }
        messages.register(rows, true);
    }

    /** The queues this service has a listener for. Read every tick: containers can be stopped. */
    private List<String> consumedQueues() {
        return containers.getListenerContainers().stream()
                .filter(AbstractMessageListenerContainer.class::isInstance)
                .flatMap(container -> ((AbstractMessageListenerContainer<?, ?, ?>) container).getQueueNames().stream())
                .distinct()
                .toList();
    }

    // Blocking join: this runs on its own thread and has nothing to do until the answer arrives.
    private long deadLetterCount(String queue) {
        String url = deadLetterQueueUrls.computeIfAbsent(queue, q -> {
            String queueUrl = sqsAsyncClient.getQueueUrl(r -> r.queueName(q)).join().queueUrl();
            String deadLetterQueue = redrivePolicies.get(queueUrl).join().deadLetterQueueOr(q);
            return sqsAsyncClient.getQueueUrl(r -> r.queueName(deadLetterQueue)).join().queueUrl();
        });
        return Long.parseLong(sqsAsyncClient
                .getQueueAttributes(r -> r.queueUrl(url)
                        .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                .join().attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));
    }
}
