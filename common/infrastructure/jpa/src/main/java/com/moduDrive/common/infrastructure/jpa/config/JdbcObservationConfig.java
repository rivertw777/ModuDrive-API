package com.moduDrive.common.infrastructure.jpa.config;

import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import net.ttddyy.observation.tracing.DataSourceBaseContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(DataSourceBaseContext.class)
public class JdbcObservationConfig {

    /** JDBC spans only inside something already traced — a request, a consumer, a relayed event.
     * The outbox relay polls every second outside any trace (and Flyway runs at startup); without
     * this each poll would start a one-span trace of its own and bury the real ones. */
    @Bean
    public ObservationPredicate jdbcOnlyWithinTrace(ObjectProvider<ObservationRegistry> registry) {
        return (name, context) -> !(context instanceof DataSourceBaseContext)
                || registry.getObject().getCurrentObservation() != null;
    }
}
