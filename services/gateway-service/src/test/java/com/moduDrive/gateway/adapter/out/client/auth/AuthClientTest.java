package com.moduDrive.gateway.adapter.out.client.auth;

import com.moduDrive.common.api.dto.auth.ValidateSessionRequest;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AuthClientTest {

    @Nested
    @DisplayName("auth-service 응답이 멈춰 있을 때 (#206)")
    class WhenTheResponseStalls {

        @Test
        void failsWithTimeoutInsteadOfHangingForever() {
            // A response that starts but never completes — the same shape as an auth-service GC
            // pause or a stalled Redis lookup; without AuthClient's own .timeout(), this Mono
            // would simply never terminate.
            WebClient stallingClient = WebClient.builder()
                    .exchangeFunction(request -> Mono.never())
                    .build();
            AuthClient authClient = new AuthClient(stallingClient, CircuitBreakerRegistry.ofDefaults(), timeLimiters());

            StepVerifier.withVirtualTime(() -> authClient.validateSession(new ValidateSessionRequest("session-id", true)))
                    .thenAwait(Duration.ofSeconds(4))
                    .expectError(TimeoutException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("세션을 확인할 때")
    class WhenValidatingSession {

        @Test
        @DisplayName("내부 경로로, 세션 ID는 URL이 아닌 본문으로 보낸다")
        void callsInternalRouteWithSessionIdInBody() {
            AtomicReference<ClientRequest> captured = new AtomicReference<>();
            WebClient recordingClient = WebClient.builder()
                    .exchangeFunction(request -> {
                        captured.set(request);
                        return Mono.just(ClientResponse.create(HttpStatus.OK)
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .body("{\"message\":\"success\",\"data\":{\"memberId\":\"member-id\",\"memberRoles\":[]}}")
                                .build());
                    })
                    .build();
            AuthClient authClient = new AuthClient(recordingClient, CircuitBreakerRegistry.ofDefaults(), timeLimiters());

            StepVerifier.create(authClient.validateSession(new ValidateSessionRequest("session-id", true)))
                    .expectNextCount(1)
                    .verifyComplete();

            assertThat(captured.get().url().getPath()).isEqualTo("/internal/v1/auth/sessions/validate");
            assertThat(captured.get().url().toString()).doesNotContain("session-id");
        }
    }

    @Nested
    @DisplayName("서킷 브레이커가 열려 있을 때")
    class WhenTheCircuitIsOpen {

        @Test
        @DisplayName("auth-service를 부르지 않고 바로 실패한다")
        void failsWithoutCallingAuthService() {
            AtomicInteger calls = new AtomicInteger();
            WebClient countingClient = WebClient.builder()
                    .exchangeFunction(request -> {
                        calls.incrementAndGet();
                        return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                    })
                    .build();
            CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
            AuthClient authClient = new AuthClient(countingClient, registry, timeLimiters());
            registry.circuitBreaker("authServiceCircuitBreaker").transitionToOpenState();

            StepVerifier.create(authClient.validateSession(new ValidateSessionRequest("session-id", true)))
                    .expectError(CallNotPermittedException.class)
                    .verify();

            assertThat(calls).hasValue(0);
        }
    }

    private static TimeLimiterRegistry timeLimiters() {
        return TimeLimiterRegistry.of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(3)).build());
    }
}
