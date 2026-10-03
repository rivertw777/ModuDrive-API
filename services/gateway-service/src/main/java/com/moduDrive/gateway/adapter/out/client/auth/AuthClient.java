package com.moduDrive.gateway.adapter.out.client.auth;

import com.moduDrive.common.api.dto.auth.ValidateSessionRequest;
import com.moduDrive.common.api.dto.auth.ValidateSessionResponse;
import com.moduDrive.common.core.web.ApiResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;


@Component
public class AuthClient {

    private final WebClient authWebClient;
    // Shared with the auth-service route (spec 006 2-2): both calls go to the same service, so either
    // one seeing it down stops the other too.
    private final CircuitBreaker circuitBreaker;
    // Its own instance: the routes' default (15s) is far too long for a check every request waits on.
    private final TimeLimiter timeLimiter;

    public AuthClient(WebClient authWebClient,
                      CircuitBreakerRegistry circuitBreakerRegistry,
                      TimeLimiterRegistry timeLimiterRegistry) {
        this.authWebClient = authWebClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("authServiceCircuitBreaker");
        this.timeLimiter = timeLimiterRegistry.timeLimiter("authSessionTimeLimiter");
    }

    public Mono<ApiResponse<ValidateSessionResponse>> validateSession(ValidateSessionRequest request) {
        return authWebClient.post()
                .uri("/internal/v1/auth/sessions/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<ValidateSessionResponse>>() {})
                // Backstop above the WebClient's own connect/read timeouts (WebClientConfig) — a
                // response that starts but stalls partway through (slow body write) is still
                // bounded here, so a caller waiting on this Mono can never hang indefinitely (#206).
                .transformDeferred(TimeLimiterOperator.of(timeLimiter))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }
}
