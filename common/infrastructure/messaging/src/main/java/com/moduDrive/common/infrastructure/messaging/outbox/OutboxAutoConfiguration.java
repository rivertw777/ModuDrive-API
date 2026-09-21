package com.moduDrive.common.infrastructure.messaging.outbox;

import com.moduDrive.common.infrastructure.messaging.MessagePublisher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Transactional outbox for a service that publishes events. Opt-in per service
 * ({@code modudrive.messaging.outbox.enabled: true}): switching it on is what maps {@code outbox_event},
 * so a service that only consumes isn't asked for a table it doesn't have. Each producing service
 * creates the table in its own Flyway migration.
 * <p>
 * Tracing is optional: without a {@link Tracer} and {@link ObservationRegistry} (e.g. in a test
 * context) events are recorded and relayed without trace context.
 */
@AutoConfiguration
@AutoConfigurationPackage
@ConditionalOnClass(EntityManagerFactory.class)
@ConditionalOnBooleanProperty("modudrive.messaging.outbox.enabled")
public class OutboxAutoConfiguration {

    @Bean
    OutboxEventRecorder outboxEventRecorder(EntityManagerFactory entityManagerFactory,
                                            PlatformTransactionManager transactionManager,
                                            JsonMapper jsonMapper,
                                            ObjectProvider<Tracer> tracer,
                                            ObjectProvider<Propagator> propagator) {
        return new OutboxEventRecorder(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory),
                new TransactionTemplate(transactionManager), jsonMapper,
                tracer.getIfAvailable(() -> Tracer.NOOP), propagator.getIfAvailable(() -> Propagator.NOOP));
    }

    /** Without a {@link MeterRegistry} the gauges simply aren't published; nothing else depends on them. */
    @Bean
    OutboxMetrics outboxMetrics(EntityManagerFactory entityManagerFactory, ObjectProvider<MeterRegistry> meterRegistry) {
        OutboxMetrics metrics =
                new OutboxMetrics(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
        meterRegistry.ifAvailable(metrics::bindTo);
        return metrics;
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    OutboxRelay outboxRelay(EntityManagerFactory entityManagerFactory,
                            PlatformTransactionManager transactionManager,
                            MessagePublisher messagePublisher,
                            JsonMapper jsonMapper,
                            ObjectProvider<ObservationRegistry> observationRegistry,
                            OutboxMetrics metrics) {
        return new OutboxRelay(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory),
                new TransactionTemplate(transactionManager), messagePublisher, jsonMapper,
                observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP), metrics);
    }
}
