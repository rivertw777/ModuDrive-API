package com.moduDrive.common.infrastructure.sqs;

import io.awspring.cloud.sqs.listener.errorhandler.AsyncErrorHandler;
import jakarta.persistence.EntityManagerFactory;
import io.awspring.cloud.sqs.listener.errorhandler.ExponentialBackoffErrorHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/** Wiring every consumer of this module gets: the listener error handler and the idempotency store
 * behind {@link ProcessedEvents}. A service can replace either by declaring its own bean. */
// afterName, not after: neither backend's auto-configuration is on every consumer's classpath.
@AutoConfiguration(afterName = {"org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration"})
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

    /** The idempotency backend comes from what the consumer actually has: its own database, else Redis.
     * Both checks are needed — the class can be on the classpath with no bean behind it. */
    @Configuration(proxyBeanMethods = false)
    @AutoConfigurationPackage
    @ConditionalOnClass(EntityManagerFactory.class)
    @ConditionalOnBean(EntityManagerFactory.class)
    static class JpaIdempotency {

        @Bean(initMethod = "start", destroyMethod = "stop")
        @ConditionalOnMissingBean(ProcessedEvents.class)
        JpaProcessedEvents jpaProcessedEvents(EntityManagerFactory entityManagerFactory,
                                              PlatformTransactionManager transactionManager) {
            return new JpaProcessedEvents(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory),
                    new TransactionTemplate(transactionManager));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    static class RedisIdempotency {

        @Bean
        @ConditionalOnMissingBean(ProcessedEvents.class)
        RedisProcessedEvents redisProcessedEvents(StringRedisTemplate redisTemplate) {
            return new RedisProcessedEvents(redisTemplate);
        }
    }
}
