package com.moduDrive.gateway.config;

import com.moduDrive.common.core.web.GlobalExceptionHandler;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Runs inside a route's circuit (RouteConfig): a 503 from the service fails the call — so the circuit
 * counts it — unless the service marked it {@code X-Dependency-Unavailable} (spec 006 2-1-2). That one
 * the service gave because its dependency (S3) is down, and it already fails fast; it goes back to the
 * client as the service answered it.
 */
class UnmarkedServiceUnavailableFilter implements GatewayFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.defer(() -> {
            ServerHttpResponse response = exchange.getResponse();
            if (HttpStatus.SERVICE_UNAVAILABLE.equals(response.getStatusCode())
                    && !response.getHeaders().containsHeader(GlobalExceptionHandler.DEPENDENCY_UNAVAILABLE_HEADER)) {
                return Mono.error(new ServiceUnavailableResponseException());
            }
            return Mono.empty();
        }));
    }

    /** The service answered an unmarked 503 — recorded by the route circuits (gateway application.yml). */
    public static class ServiceUnavailableResponseException extends RuntimeException {

        ServiceUnavailableResponseException() {
            super("503 from the service", null, false, false);
        }
    }
}
