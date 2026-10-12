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
 * Publishes the DLQ depth of each queue this service consumes, so an alert fires when a consumer gave up
 * on a message (otherwise it sits there silently until SQS retention drops it). Any non-zero value is a
 * problem. Tagged by source queue, like the other messaging metrics; the DLQ is resolved once per queue
 * from its {@link RedrivePolicy}. Polls GetQueueAttributes because the consumer never sees the DLQ.
 */
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
            return; // no MeterRegistry: nothing to publish, so don't poll
        }
        messages = MultiGauge.builder(METER_NAME)
                .description("Messages parked in a consumed queue's dead letter queue")
                .register(meterRegistry);
        // Own thread, not Boot's shared scheduler: the SQS calls below block.
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("dlq-metrics").factory());
        // First tick waits an interval: listener containers may not be registered yet.
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

    /** One series per consumed queue, zeroes included (they show the poll is alive). A queue whose DLQ
     * can't be read is skipped rather than reported as 0. */
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
