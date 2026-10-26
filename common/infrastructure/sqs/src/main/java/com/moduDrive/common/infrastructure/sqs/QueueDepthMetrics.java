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
 * Publishes, for each queue this service consumes, the messages waiting in it and in its DLQ. The DLQ depth
 * lets an alert fire when a consumer gave up on a message (otherwise it sits there silently until SQS
 * retention drops it) — any non-zero value is a problem. The queue's own depth shows a consumer falling
 * behind. Tagged by source queue, like the other messaging metrics; the DLQ is resolved once per queue
 * from its {@link RedrivePolicy}. Polls GetQueueAttributes because the consumer never sees the DLQ.
 */
@Slf4j
class QueueDepthMetrics {

    static final String METER_NAME = "modudrive.dlq.messages";
    static final String QUEUE_METER_NAME = "modudrive.queue.messages";
    // The alert evaluates every minute; anything finer just spends API calls.
    private static final int INTERVAL_SECONDS = 30;

    private final SqsAsyncClient sqsAsyncClient;
    private final MessageListenerContainerRegistry containers;
    private final MeterRegistry meterRegistry;
    private final RedrivePolicy.Cache redrivePolicies;
    private final Map<String, String> queueUrls = new ConcurrentHashMap<>();
    private final Map<String, String> deadLetterQueueUrls = new ConcurrentHashMap<>();
    private MultiGauge messages;
    private MultiGauge queueMessages;
    private ScheduledExecutorService executor;

    QueueDepthMetrics(SqsAsyncClient sqsAsyncClient, MessageListenerContainerRegistry containers,
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
        queueMessages = MultiGauge.builder(QUEUE_METER_NAME)
                .description("Messages waiting in a consumed queue, not yet received (in-flight and delayed left out)")
                .register(meterRegistry);
        // Own thread, not Boot's shared scheduler: the SQS calls below block.
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("queue-metrics").factory());
        // First tick waits an interval: listener containers may not be registered yet.
        executor.scheduleWithFixedDelay(() -> {
            try {
                refresh();
            } catch (Exception e) {
                // An escaped exception would cancel the schedule for good.
                log.error("Queue metrics tick failed", e);
            }
        }, INTERVAL_SECONDS, INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /** One series per consumed queue, zeroes included (they show the poll is alive). A queue that
     * can't be read is skipped rather than reported as 0. */
    void refresh() {
        List<MultiGauge.Row<?>> rows = new ArrayList<>();
        List<MultiGauge.Row<?>> queueRows = new ArrayList<>();
        for (String queue : consumedQueues()) {
            try {
                rows.add(MultiGauge.Row.of(Tags.of("queue", queue), deadLetterCount(queue)));
            } catch (Exception e) {
                log.warn("Couldn't read the DLQ of {}", queue, e);
            }
            try {
                queueRows.add(MultiGauge.Row.of(Tags.of("queue", queue), count(queueUrl(queue))));
            } catch (Exception e) {
                log.warn("Couldn't read the depth of {}", queue, e);
            }
        }
        messages.register(rows, true);
        queueMessages.register(queueRows, true);
    }

    /** The queues this service has a listener for. Read every tick: containers can be stopped. */
    private List<String> consumedQueues() {
        return containers.getListenerContainers().stream()
                .filter(AbstractMessageListenerContainer.class::isInstance)
                .flatMap(container -> ((AbstractMessageListenerContainer<?, ?, ?>) container).getQueueNames().stream())
                .distinct()
                .toList();
    }

    // Blocking joins: this runs on its own thread and has nothing to do until the answer arrives.
    private long deadLetterCount(String queue) {
        return count(deadLetterQueueUrls.computeIfAbsent(queue, q -> {
            String deadLetterQueue = redrivePolicies.get(queueUrl(q)).join().deadLetterQueueOr(q);
            return sqsAsyncClient.getQueueUrl(r -> r.queueName(deadLetterQueue)).join().queueUrl();
        }));
    }

    private String queueUrl(String queue) {
        return queueUrls.computeIfAbsent(queue, q -> sqsAsyncClient.getQueueUrl(r -> r.queueName(q)).join().queueUrl());
    }

    private long count(String url) {
        return Long.parseLong(sqsAsyncClient
                .getQueueAttributes(r -> r.queueUrl(url)
                        .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                .join().attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));
    }
}
