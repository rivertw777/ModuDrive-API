package com.moduDrive.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@RequiredArgsConstructor
@Configuration
class RouteConfig {

    private final Environment env;

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("member-service", r -> r.path("/api/v1/member/**")
                        .filters(f -> addCircuitBreaker(f, "memberServiceCircuitBreaker"))
                        .uri(url("member-service")))
                .route("auth-service", r -> r.path("/api/v1/auth/**")
                        .filters(f -> addCircuitBreaker(f, "authServiceCircuitBreaker"))
                        .uri(url("auth-service")))
                .route("file-service", r -> r.path("/api/v1/files/**", "/api/v1/directories/**")
                        .filters(f -> addCircuitBreaker(f, "fileServiceCircuitBreaker"))
                        .uri(url("file-service")))
                .route("storage-service", r -> r.path("/api/v1/storage/**")
                        .filters(f -> addCircuitBreaker(f, "storageServiceCircuitBreaker"))
                        .uri(url("storage-service")))
                .route("notification-service", r -> r.path("/api/v1/notifications/**")
                        .filters(f -> addCircuitBreaker(f, "notificationServiceCircuitBreaker"))
                        .uri(url("notification-service")))
                .build();
    }

    private String url(String service) {
        return env.getRequiredProperty("clients." + service + ".url");
    }

    private GatewayFilterSpec addCircuitBreaker(GatewayFilterSpec filterSpec, String circuitBreakerName) {
        return filterSpec.circuitBreaker(c -> c.setName(circuitBreakerName)
                .setFallbackUri("forward:/fallback/default"));
    }

}
