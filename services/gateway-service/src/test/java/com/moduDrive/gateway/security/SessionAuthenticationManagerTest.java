package com.moduDrive.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.api.dto.auth.ValidateSessionRequest;
import com.moduDrive.common.api.dto.auth.ValidateSessionResponse;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.common.infrastructure.resilience4j.CircuitBreakerExceptionCase;
import com.moduDrive.gateway.client.AuthClient;
import com.moduDrive.gateway.exception.AuthExceptionCase;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SessionAuthenticationManagerTest {

    @Mock
    private AuthClient authClient;

    private SessionAuthenticationManager manager;

    private final SessionAuthenticationToken token = new SessionAuthenticationToken("session-id", true);

    @BeforeEach
    void setUp() {
        manager = new SessionAuthenticationManager(authClient, new ObjectMapper());
    }

    private static void assertSessionFailure(Throwable e, String status, String message) {
        assertThat(e).isInstanceOf(SessionAuthenticationException.class);
        assertThat(((SessionAuthenticationException) e).getStatus()).isEqualTo(status);
        assertThat(e.getMessage()).isEqualTo(message);
    }

    @Nested
    @DisplayName("살아 있는 세션일 때")
    class WhenSessionIsValid {

        @Test
        void returnsAuthenticationWithAllRolesAsIndividualAuthorities() {
            given(authClient.validateSession(new ValidateSessionRequest("session-id", true)))
                    .willReturn(Mono.just(ApiResponse.success(
                            new ValidateSessionResponse("member-id", List.of("MEMBER", "ADMIN")))));

            StepVerifier.create(manager.authenticate(token))
                    .assertNext(auth -> {
                        assertThat(auth.isAuthenticated()).isTrue();
                        assertThat(auth.getPrincipal()).isEqualTo("member-id");
                        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("MEMBER", "ADMIN");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("touch=false 토큰은 touch=false로 확인한다")
        void passesTouchFlagThrough() {
            given(authClient.validateSession(new ValidateSessionRequest("session-id", false)))
                    .willReturn(Mono.just(ApiResponse.success(
                            new ValidateSessionResponse("member-id", List.of("MEMBER")))));

            StepVerifier.create(manager.authenticate(new SessionAuthenticationToken("session-id", false)))
                    .expectNextCount(1)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("응답 data가 비어 있을 때")
    class WhenResponseDataIsNull {

        @Test
        void failsWithUnauthorized() {
            given(authClient.validateSession(any(ValidateSessionRequest.class)))
                    .willReturn(Mono.just(ApiResponse.success()));

            StepVerifier.create(manager.authenticate(token))
                    .verifyErrorSatisfies(e -> assertSessionFailure(e,
                            "UNAUTHORIZED", AuthExceptionCase.UNAUTHORIZED.getMessage()));
        }
    }

    @Nested
    @DisplayName("auth-service가 4xx로 거절할 때")
    class WhenAuthServiceRejects {

        @Test
        void failsWithAuthServiceStatusAndMessage() {
            String errorBody = "{\"status\":\"UNAUTHORIZED\",\"message\":\"세션이 만료되었습니다.\"}";
            given(authClient.validateSession(any(ValidateSessionRequest.class))).willReturn(Mono.error(
                    WebClientResponseException.create(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", null,
                            errorBody.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)));

            StepVerifier.create(manager.authenticate(token))
                    .verifyErrorSatisfies(e -> assertSessionFailure(e, "UNAUTHORIZED", "세션이 만료되었습니다."));
        }

        @Test
        void fallsBackToUnauthorizedWhenBodyIsNotJson() {
            given(authClient.validateSession(any(ValidateSessionRequest.class))).willReturn(Mono.error(
                    WebClientResponseException.create(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", null,
                            "not json".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)));

            StepVerifier.create(manager.authenticate(token))
                    .verifyErrorSatisfies(e -> assertSessionFailure(e,
                            "UNAUTHORIZED", AuthExceptionCase.UNAUTHORIZED.getMessage()));
        }
    }

    @Nested
    @DisplayName("auth-service가 5xx로 답할 때")
    class WhenAuthServiceFails {

        @Test
        @DisplayName("세션 만료가 아니라 확인 불가(503)로 실패한다")
        void failsWithServiceUnavailable() {
            String errorBody = "{\"status\":\"SERVICE_UNAVAILABLE\",\"message\":\"Redis down\"}";
            given(authClient.validateSession(any(ValidateSessionRequest.class))).willReturn(Mono.error(
                    WebClientResponseException.create(HttpStatus.SERVICE_UNAVAILABLE.value(), "Service Unavailable",
                            null, errorBody.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)));

            StepVerifier.create(manager.authenticate(token))
                    .verifyErrorSatisfies(e -> assertSessionFailure(e,
                            "SERVICE_UNAVAILABLE", CircuitBreakerExceptionCase.SERVICE_UNAVAILABLE.getMessage()));
        }

        @Test
        @DisplayName("504면 시간 초과로 실패한다")
        void failsWithGatewayTimeoutOn504() {
            given(authClient.validateSession(any(ValidateSessionRequest.class))).willReturn(Mono.error(
                    WebClientResponseException.create(HttpStatus.GATEWAY_TIMEOUT.value(), "Gateway Timeout",
                            null, new byte[0], StandardCharsets.UTF_8)));

            StepVerifier.create(manager.authenticate(token))
                    .verifyErrorSatisfies(e -> assertSessionFailure(e,
                            "GATEWAY_TIMEOUT", CircuitBreakerExceptionCase.CONNECTION_TIMEOUT.getMessage()));
        }
    }

    @Nested
    @DisplayName("auth-service가 답하지 못했을 때")
    class WhenAuthServiceDoesNotAnswer {

        @Test
        @DisplayName("서킷이 열려 있으면 차단(503)으로 실패한다")
        void failsWithOpenCircuit() {
            given(authClient.validateSession(any(ValidateSessionRequest.class))).willReturn(Mono.error(
                    CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("test"))));

            StepVerifier.create(manager.authenticate(token))
                    .verifyErrorSatisfies(e -> assertSessionFailure(e,
                            "SERVICE_UNAVAILABLE", CircuitBreakerExceptionCase.SERVICE_IS_OPEN.getMessage()));
        }

        @Test
        @DisplayName("시간 초과면 504로 실패한다")
        void failsWithGatewayTimeout() {
            given(authClient.validateSession(any(ValidateSessionRequest.class)))
                    .willReturn(Mono.error(new TimeoutException("3s")));

            StepVerifier.create(manager.authenticate(token))
                    .verifyErrorSatisfies(e -> assertSessionFailure(e,
                            "GATEWAY_TIMEOUT", CircuitBreakerExceptionCase.CONNECTION_TIMEOUT.getMessage()));
        }

        @Test
        @DisplayName("연결 실패 등은 연결 불가(503)로 실패한다")
        void failsWithServiceUnavailable() {
            given(authClient.validateSession(any(ValidateSessionRequest.class)))
                    .willReturn(Mono.error(new RuntimeException("connection refused")));

            StepVerifier.create(manager.authenticate(token))
                    .verifyErrorSatisfies(e -> assertSessionFailure(e,
                            "SERVICE_UNAVAILABLE", CircuitBreakerExceptionCase.SERVICE_UNAVAILABLE.getMessage()));
        }
    }
}
