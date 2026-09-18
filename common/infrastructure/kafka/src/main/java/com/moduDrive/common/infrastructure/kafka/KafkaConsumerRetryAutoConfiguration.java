package com.moduDrive.common.infrastructure.kafka;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Gives every {@link KafkaListener} retry-with-backoff plus a dead-letter topic, so a failed
 * business call (DB down, downstream 500, ...) can't wedge a consumer forever. Does NOT cover a
 * malformed/undeserializable message (a "poison pill") unless the service also configures
 * {@code ErrorHandlingDeserializer} — without it, {@link DefaultErrorHandler} refuses to handle
 * the resulting {@code SerializationException} and the record replays forever. The shared
 * {@code application-kafka.yml} in this module configures it for every service that imports it.
 * <p>
 * Spring Boot's auto-configured listener container factory resolves a {@code CommonErrorHandler}
 * bean via {@code ObjectProvider.getIfUnique()} and wires in whatever it finds, so registering
 * this bean is enough — no need to redefine the container factory itself. That also means a
 * second {@code CommonErrorHandler} bean anywhere in a service's context makes {@code getIfUnique()}
 * return {@code null}, silently dropping DLQ/retry for that service with no error — keep this
 * bean the only one of its type.
 * <p>
 * The dead-letter record goes to {@code <topic>-dlt} with no partition pinned. The recoverer's
 * default reuses the source partition number, so a DLT with fewer partitions than its source
 * makes recovery itself fail, and the record replays forever. The DLT is auto-created by the
 * broker; a cluster with {@code auto.create.topics.enable=false} must provision it up front.
 * <p>
 * ponytail: fixed retry policy for every topic; split per-topic if one SLA needs to differ.
 */
@AutoConfiguration
@ConditionalOnClass(KafkaListener.class)
public class KafkaConsumerRetryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    CommonErrorHandler kafkaDeadLetterErrorHandler(KafkaOperations<Object, Object> kafkaOperations) {
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxAttempts(3); // 1s, 2s, 4s, then give up
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaOperations,
                (record, ex) -> new TopicPartition(record.topic() + "-dlt", -1)), backOff);
    }
}
