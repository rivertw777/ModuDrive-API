package com.moduDrive.common.infrastructure.outbox;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Transactional outbox for every service that depends on this module. {@link AutoConfigurationPackage}
 * adds this package to Boot's entity scan, so {@code ddl-auto=update} creates {@code outbox_event}
 * next to the service's own tables with no extra setup in the service.
 * <p>
 * Tracing is optional: without a {@link Tracer} (e.g. in a test context) events are relayed
 * without trace headers.
 */
@AutoConfiguration
@AutoConfigurationPackage
@EnableScheduling
public class OutboxAutoConfiguration {

    @Bean
    OutboxEventRecorder outboxEventRecorder(@Value("${spring.application.name}") String source,
                                            EntityManagerFactory entityManagerFactory,
                                            PlatformTransactionManager transactionManager,
                                            JsonMapper jsonMapper,
                                            ObjectProvider<Tracer> tracer,
                                            ObjectProvider<Propagator> propagator) {
        return new OutboxEventRecorder(source, SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory),
                new TransactionTemplate(transactionManager), jsonMapper,
                tracer.getIfAvailable(() -> Tracer.NOOP), propagator.getIfAvailable(() -> Propagator.NOOP));
    }

    @Bean
    OutboxRelay outboxRelay(@Value("${spring.application.name}") String source,
                            EntityManagerFactory entityManagerFactory,
                            PlatformTransactionManager transactionManager,
                            KafkaTemplate<Object, Object> kafkaTemplate,
                            JsonMapper jsonMapper,
                            ObjectProvider<Tracer> tracer,
                            ObjectProvider<Propagator> propagator) {
        return new OutboxRelay(source, SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory),
                new TransactionTemplate(transactionManager), kafkaTemplate, jsonMapper,
                tracer.getIfAvailable(() -> Tracer.NOOP), propagator.getIfAvailable(() -> Propagator.NOOP));
    }
}
