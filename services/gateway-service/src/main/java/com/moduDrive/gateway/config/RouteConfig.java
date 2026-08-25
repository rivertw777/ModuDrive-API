package com.moduDrive.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RouteConfig {

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("member-service", r -> r.path("/api/v1/member/**")
                        .filters(f -> addCircuitBreaker(f, "memberServiceCircuitBreaker"))
                        .uri("lb://member-service"))
                .route("auth-service", r -> r.path("/api/v1/auth/**")
                        .filters(f -> addCircuitBreaker(f, "authServiceCircuitBreaker"))
                        .uri("lb://auth-service"))
                .route("file-service", r -> r.path("/api/v1/files/**", "/api/v1/directories/**")
                        .filters(f -> addCircuitBreaker(f, "fileServiceCircuitBreaker"))
                        .uri("lb://file-service"))
                .route("storage-service", r -> r.path("/api/v1/storage/**")
                        .filters(f -> addCircuitBreaker(f, "storageServiceCircuitBreaker"))
                        .uri("lb://storage-service"))
                .route("mail-service", r -> r.path("/api/v1/mail/**")
                        .filters(f -> addCircuitBreaker(f, "mailServiceCircuitBreaker"))
                        .uri("lb://mail-service"))
                .build();
    }

    private GatewayFilterSpec addCircuitBreaker(GatewayFilterSpec filterSpec, String circuitBreakerName) {
        return filterSpec.circuitBreaker(c -> c.setName(circuitBreakerName)
                .setFallbackUri("forward:/fallback/default"));
    }

}
