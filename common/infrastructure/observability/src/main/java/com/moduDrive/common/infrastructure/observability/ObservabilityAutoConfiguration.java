package com.moduDrive.common.infrastructure.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/** Servlet services only — the gateway (WebFlux) sets {@code X_USER_ID}, it doesn't read it, and has
 * no servlet classes to load this with. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ObservabilityAutoConfiguration {

    /** Runs right after Boot's ServerHttpObservationFilter (HIGHEST_PRECEDENCE + 1), which must
     * have opened the server observation for the tag to land on it. */
    @Bean
    FilterRegistrationBean<UserIdObservationFilter> userIdObservationFilter() {
        FilterRegistrationBean<UserIdObservationFilter> registration =
                new FilterRegistrationBean<>(new UserIdObservationFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }
}
