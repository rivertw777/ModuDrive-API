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

    /** JDBC spans only inside an existing trace. The outbox relay polls every second outside any trace
     * (as does Flyway at startup), and each poll would otherwise start its own one-span trace. */
    @Bean
    public ObservationPredicate jdbcOnlyWithinTrace(ObjectProvider<ObservationRegistry> registry) {
        return (name, context) -> !(context instanceof DataSourceBaseContext)
                || registry.getObject().getCurrentObservation() != null;
    }
}
