package com.moduDrive.common.infrastructure.messaging.idempotency;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lets a consumer skip a message it already handled. Opt-in per service
 * ({@code modudrive.messaging.idempotency.enabled: true}), so a service that only publishes isn't asked
 * for a {@code processed_event} table it doesn't have. The backend follows what the service has: its own
 * database, else Redis.
 */
@AutoConfiguration
@ConditionalOnBooleanProperty("modudrive.messaging.idempotency.enabled")
public class IdempotencyAutoConfiguration {

    /** Switching this on is what maps {@code processed_event}; the service creates it in its own
     * Flyway migration. */
    @Configuration(proxyBeanMethods = false)
    @AutoConfigurationPackage
    @ConditionalOnClass(EntityManagerFactory.class)
    static class Jpa {

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
    static class Redis {

        @Bean
        @ConditionalOnMissingBean(ProcessedEvents.class)
        RedisProcessedEvents redisProcessedEvents(StringRedisTemplate redisTemplate) {
            return new RedisProcessedEvents(redisTemplate);
        }
    }
}
