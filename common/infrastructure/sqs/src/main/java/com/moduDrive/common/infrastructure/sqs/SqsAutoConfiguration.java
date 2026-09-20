package com.moduDrive.common.infrastructure.sqs;

import com.moduDrive.common.infrastructure.messaging.MessagePublisher;
import io.awspring.cloud.sqs.listener.errorhandler.AsyncErrorHandler;
import io.awspring.cloud.sqs.listener.errorhandler.ExponentialBackoffErrorHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/** The SQS adapter's wiring: {@link MessagePublisher} for producers and the listener error handler for
 * consumers. A service can replace either by declaring its own bean. */
@AutoConfiguration
public class SqsAutoConfiguration {

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

    @Bean
    @ConditionalOnMissingBean(MessagePublisher.class)
    MessagePublisher sqsMessagePublisher(SqsTemplate sqsTemplate) {
        return new SqsMessagePublisher(sqsTemplate);
    }
}
