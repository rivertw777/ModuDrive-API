package com.moduDrive.common.infrastructure.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConsumerRetryAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaConsumerRetryAutoConfiguration.class, KafkaAutoConfiguration.class));

    @Test
    void attachesTheDeadLetterBackedErrorHandlerToTheAutoConfiguredListenerContainerFactory() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CommonErrorHandler.class);
            CommonErrorHandler errorHandler = context.getBean(CommonErrorHandler.class);
            assertThat(errorHandler).isInstanceOf(DefaultErrorHandler.class);

            // Boot's own factory bean, not one we define — proves it actually picked up our
            // bean via ObjectProvider, not just that both beans happen to exist independently.
            ConcurrentKafkaListenerContainerFactory<?, ?> factory =
                    context.getBean(ConcurrentKafkaListenerContainerFactory.class);
            assertThat(factory.createContainer("some-topic").getCommonErrorHandler())
                    .isSameAs(errorHandler);
        });
    }

    @Test
    void backsOffWhenAServiceDefinesItsOwnErrorHandler() {
        contextRunner.withUserConfiguration(CustomErrorHandlerConfig.class).run(context -> {
            assertThat(context).hasSingleBean(CommonErrorHandler.class);
            assertThat(context.getBean(CommonErrorHandler.class))
                    .isSameAs(CustomErrorHandlerConfig.CUSTOM_HANDLER);

            ConcurrentKafkaListenerContainerFactory<?, ?> factory =
                    context.getBean(ConcurrentKafkaListenerContainerFactory.class);
            assertThat(factory.createContainer("some-topic").getCommonErrorHandler())
                    .isSameAs(CustomErrorHandlerConfig.CUSTOM_HANDLER);
        });
    }

    @Configuration
    static class CustomErrorHandlerConfig {
        static final CommonErrorHandler CUSTOM_HANDLER = mock(CommonErrorHandler.class);

        @Bean
        CommonErrorHandler commonErrorHandler() {
            return CUSTOM_HANDLER;
        }
    }
}
