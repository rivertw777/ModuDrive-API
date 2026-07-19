package com.moduDrive.gateway.adapter.in.web.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile("dev")
@Configuration
class SwaggerRouteConfig {

    @Bean
    public RouteLocator swaggerRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("member-service-swagger", r -> r.path(
                        "/member-service/v3/api-docs",
                        "/member-service/v3/api-docs/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri("lb://member-service"))
                .route("auth-service-swagger", r -> r.path(
                        "/auth-service/v3/api-docs",
                        "/auth-service/v3/api-docs/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri("lb://auth-service"))
                .route("file-service-swagger", r -> r.path(
                        "/file-service/v3/api-docs",
                        "/file-service/v3/api-docs/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri("lb://file-service"))
                .route("storage-service-swagger", r -> r.path(
                        "/storage-service/v3/api-docs",
                        "/storage-service/v3/api-docs/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri("lb://storage-service"))
                .route("notification-service-swagger", r -> r.path(
                        "/notification-service/v3/api-docs",
                        "/notification-service/v3/api-docs/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri("lb://notification-service"))
                .build();
    }
}
