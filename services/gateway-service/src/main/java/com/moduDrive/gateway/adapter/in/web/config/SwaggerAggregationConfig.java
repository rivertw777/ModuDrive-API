package com.moduDrive.gateway.adapter.in.web.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties.SwaggerUrl;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Profile("dev")
@Configuration
@RequiredArgsConstructor
class SwaggerAggregationConfig {

    private static final List<String> AGGREGATED_SERVICES = List.of(
            "member-service",
            "auth-service",
            "file-service",
            "storage-service",
            "mail-service",
            "notification-service"
    );

    private final SwaggerUiConfigProperties swaggerUiConfigProperties;

    @PostConstruct
    void configureSwaggerUiUrls() {
        Set<SwaggerUrl> urls = new LinkedHashSet<>();
        AGGREGATED_SERVICES.forEach(service ->
                urls.add(new SwaggerUrl(service, "/" + service + "/v3/api-docs", service)));

        swaggerUiConfigProperties.setUrls(urls);
        swaggerUiConfigProperties.setPath("/swagger-ui.html");
    }

    @Bean
    public RouteLocator swaggerRoutes(RouteLocatorBuilder builder) {
        RouteLocatorBuilder.Builder routes = builder.routes();
        AGGREGATED_SERVICES.forEach(service -> routes.route(service + "-swagger", r -> r.path(
                        "/" + service + "/v3/api-docs",
                        "/" + service + "/v3/api-docs/**")
                .filters(f -> f.stripPrefix(1))
                .uri("lb://" + service)));
        return routes.build();
    }
}
