package com.moduDrive.common.infrastructure.sqs;

import io.awspring.cloud.sqs.listener.errorhandler.AsyncErrorHandler;
import io.awspring.cloud.sqs.listener.errorhandler.ExponentialBackoffErrorHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/** Spring Cloud AWS's default listener container factory picks up this {@link AsyncErrorHandler}
 * bean, so every {@code @SqsListener} in a service using this module gets it. A service can replace it
 * by declaring its own. */
@AutoConfiguration
public class SqsErrorHandlingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AsyncErrorHandler.class)
    AsyncErrorHandler<Object> deadLetteringErrorHandler(SqsAsyncClient sqsAsyncClient) {
        // Retry delays 1s, 2s, 4s; the queue's maxReceiveCount (4) then redrives to the DLQ.
        AsyncErrorHandler<Object> retry = ExponentialBackoffErrorHandler.builder()
                .initialVisibilityTimeoutSeconds(1)
                .multiplier(2)
                .maxVisibilityTimeoutSeconds(10)
                .build();
        return new DeadLetteringErrorHandler(sqsAsyncClient, retry);
    }
}
