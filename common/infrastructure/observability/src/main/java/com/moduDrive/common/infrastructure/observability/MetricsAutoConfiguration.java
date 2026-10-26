package com.moduDrive.common.infrastructure.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/** Every service, the gateway (WebFlux) included — unlike {@link ObservabilityAutoConfiguration}. */
@AutoConfiguration
public class MetricsAutoConfiguration {

    @Bean
    MemoryMetrics memoryMetrics() {
        return new MemoryMetrics();
    }
}
