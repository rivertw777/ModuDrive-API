package com.moduDrive.auth.adapter.in.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Registers {@link InternalTokenFilter} on {@code /internal/*} only — the tenant-facing
 * {@code /api/v1/auth/**} routes are reached through the gateway and never carry this header.
 * Lives next to the filter rather than under {@code adapter/in/web/config} so both classes can
 * stay package-private. */
@Configuration
class InternalTokenFilterConfig {

    @Bean
    FilterRegistrationBean<InternalTokenFilter> internalTokenFilterRegistration(
            @Value("${internal.service.token}") String internalServiceToken,
            ObjectMapper objectMapper) {
        FilterRegistrationBean<InternalTokenFilter> registration =
                new FilterRegistrationBean<>(new InternalTokenFilter(internalServiceToken, objectMapper));
        registration.addUrlPatterns("/internal/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
