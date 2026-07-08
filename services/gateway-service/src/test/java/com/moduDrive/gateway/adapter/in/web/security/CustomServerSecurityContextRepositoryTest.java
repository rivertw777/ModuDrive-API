package com.moduDrive.gateway.adapter.in.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.api.dto.auth.ValidateTokenRequest;
import com.moduDrive.common.api.dto.auth.ValidateTokenResponse;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.gateway.adapter.out.auth.AuthClient;
import com.moduDrive.gateway.exception.AuthExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CustomServerSecurityContextRepositoryTest {

    @Mock
    private AuthClient authClient;
    @Mock
    private ObjectMapper objectMapper;
    @InjectMocks
    private CustomServerSecurityContextRepository repository;

    @Nested
    @DisplayName("Authorization 헤더가 없을 때")
    class WhenAuthorizationHeaderIsMissing {

        @Test
        void returnsEmptyAndSetsNoAuthTokenAttribute() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/secured").build());

            StepVerifier.create(repository.load(exchange))
                    .verifyComplete();

            assertThat(exchange.getAttributes().get(AuthErrorAttributeUtils.MESSAGE_ATTRIBUTE))
                    .isEqualTo(AuthExceptionCase.NO_AUTH_TOKEN.getMessage());
        }
    }

    @Nested
    @DisplayName("Bearer 형식이 아닌 Authorization 헤더일 때")
    class WhenAuthorizationHeaderIsNotBearerFormat {

        @Test
        void returnsEmptyAndSetsNoAuthTokenAttribute() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/secured")
                            .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                            .build());

            StepVerifier.create(repository.load(exchange))
                    .verifyComplete();

            assertThat(exchange.getAttributes().get(AuthErrorAttributeUtils.MESSAGE_ATTRIBUTE))
                    .isEqualTo(AuthExceptionCase.NO_AUTH_TOKEN.getMessage());
        }
    }

    @Nested
    @DisplayName("유효한 Bearer 토큰일 때")
    class WhenTokenIsValid {

        @Test
        void returnsSecurityContextWithAllRolesAsIndividualAuthorities() {
            ValidateTokenResponse tokenResponse = new ValidateTokenResponse("member-id", List.of("MEMBER", "ADMIN"));
            given(authClient.validateToken(any(ValidateTokenRequest.class)))
                    .willReturn(Mono.just(ApiResponse.success(tokenResponse)));

            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/secured")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                            .build());

            StepVerifier.create(repository.load(exchange))
                    .assertNext(ctx -> {
                        assertThat(ctx).isInstanceOf(SecurityContext.class);
                        assertThat(ctx.getAuthentication().getPrincipal()).isEqualTo("member-id");
                        assertThat(ctx.getAuthentication().getAuthorities())
                                .extracting("authority")
                                .containsExactly("MEMBER", "ADMIN");
                    })
                    .verifyComplete();
        }

        @Test
        void returnsEmptyWhenTokenResponseDataIsNull() {
            given(authClient.validateToken(any(ValidateTokenRequest.class)))
                    .willReturn(Mono.just(ApiResponse.success()));

            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/secured")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer token-with-null-data")
                            .build());

            StepVerifier.create(repository.load(exchange))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("토큰 검증 서비스가 WebClientResponseException을 던질 때")
    class WhenWebClientResponseExceptionOccurs {

        @Test
        void returnsEmptyAndSetsErrorAttributeFromResponseBody() throws Exception {
            String errorBody = "{\"status\":\"UNAUTHORIZED\",\"message\":\"유효하지 않은 토큰입니다.\"}";
            WebClientResponseException ex = WebClientResponseException.create(
                    HttpStatus.UNAUTHORIZED.value(), "Unauthorized", null,
                    errorBody.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

            given(authClient.validateToken(any(ValidateTokenRequest.class)))
                    .willReturn(Mono.error(ex));

            ObjectMapper realMapper = new ObjectMapper();
            given(objectMapper.readTree(errorBody)).willReturn(realMapper.readTree(errorBody));

            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/secured")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer expired-token")
                            .build());

            StepVerifier.create(repository.load(exchange))
                    .verifyComplete();

            assertThat(exchange.getAttributes().get(AuthErrorAttributeUtils.STATUS_ATTRIBUTE))
                    .isEqualTo("UNAUTHORIZED");
        }
    }

    @Nested
    @DisplayName("예상치 못한 예외가 발생할 때")
    class WhenUnexpectedExceptionOccurs {

        @Test
        void returnsEmpty() {
            given(authClient.validateToken(any(ValidateTokenRequest.class)))
                    .willReturn(Mono.error(new RuntimeException("connection refused")));

            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/secured")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer some-token")
                            .build());

            StepVerifier.create(repository.load(exchange))
                    .verifyComplete();
        }
    }
}
