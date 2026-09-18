package com.moduDrive.common.infrastructure.kafka;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.ProducerListener;

/**
 * Backs off Boot's default {@code LoggingProducerListener} (which logs the full payload on
 * failure) in favor of one that logs only topic/key, shared by every auto-configured
 * {@link KafkaTemplate} so publishers don't each wire their own send-failure logging.
 */
@AutoConfiguration(before = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaLoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ProducerListener.class)
    ProducerListener<Object, Object> kafkaProducerLoggingListener() {
        return new KafkaProducerLoggingListener();
    }
}
