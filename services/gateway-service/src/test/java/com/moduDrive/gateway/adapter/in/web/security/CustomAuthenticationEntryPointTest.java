package com.moduDrive.gateway.adapter.in.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.gateway.exception.AuthExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class CustomAuthenticationEntryPointTest {

    private final CustomAuthenticationEntryPoint entryPoint =
            new CustomAuthenticationEntryPoint(new ObjectMapper());

    @Nested
    @DisplayName("에러 속성이 설정된 상태일 때")
    class WhenErrorAttributeIsSet {

        @Test
        void returns401WithSetMessage() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/secured").build());
            AuthErrorAttributeUtils.setAuthErrorAttribute(exchange, AuthExceptionCase.NO_AUTH_TOKEN);

            StepVerifier.create(entryPoint.commence(exchange, new InsufficientAuthenticationException("test")))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            String body = exchange.getResponse().getBodyAsString().block();
            assertThat(body)
                    .contains(AuthExceptionCase.NO_AUTH_TOKEN.getMessage())
                    .contains(HttpStatus.UNAUTHORIZED.name());
        }
    }

    @Nested
    @DisplayName("에러 속성이 설정되지 않은 상태일 때")
    class WhenErrorAttributeIsNotSet {

        @Test
        void returns401WithDefaultMessage() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/secured").build());

            StepVerifier.create(entryPoint.commence(exchange, new InsufficientAuthenticationException("test")))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            String body = exchange.getResponse().getBodyAsString().block();
            assertThat(body).contains(AuthExceptionCase.UNAUTHORIZED.getMessage());
        }
    }
}
