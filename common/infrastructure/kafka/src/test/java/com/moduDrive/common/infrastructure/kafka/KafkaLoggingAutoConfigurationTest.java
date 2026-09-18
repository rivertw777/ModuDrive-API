package com.moduDrive.common.infrastructure.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.support.LoggingProducerListener;
import org.springframework.kafka.support.ProducerListener;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaLoggingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaLoggingAutoConfiguration.class, KafkaAutoConfiguration.class));

    @Test
    void attachesTheSharedLoggingListenerInsteadOfBootsDefaultOne() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ProducerListener.class);
            assertThat(context.getBean(ProducerListener.class))
                    .isInstanceOf(KafkaProducerLoggingListener.class)
                    .isNotInstanceOf(LoggingProducerListener.class);
        });
    }
}
