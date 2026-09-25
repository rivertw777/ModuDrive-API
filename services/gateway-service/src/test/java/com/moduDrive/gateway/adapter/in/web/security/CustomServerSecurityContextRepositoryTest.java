package com.moduDrive.gateway.adapter.in.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.api.dto.auth.ValidateSessionRequest;
import com.moduDrive.common.api.dto.auth.ValidateSessionResponse;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.gateway.adapter.out.client.auth.AuthClient;
import com.moduDrive.gateway.exception.AuthExceptionCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpCookie;
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
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CustomServerSecurityContextRepositoryTest {

    private static final String COOKIE_NAME = "__Host-session";

    @Mock
    private AuthClient authClient;
    @Mock
    private ObjectMapper objectMapper;

    private CustomServerSecurityContextRepository repository;

    @BeforeEach
    void setUp() {
        repository = new CustomServerSecurityContextRepository(authClient, objectMapper, true);
    }

    private static MockServerWebExchange withSessionCookie(String value) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/secured").cookie(new HttpCookie(COOKIE_NAME, value)).build());
    }

    @Nested
    @DisplayName("세션 쿠키가 없을 때")
    class WhenSessionCookieIsMissing {

        @Test
        void returnsEmptyAndSetsNoSessionAttribute() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/secured").build());

            StepVerifier.create(repository.load(exchange))
                    .verifyComplete();

            assertThat(exchange.getAttributes().get(AuthErrorAttributeUtils.MESSAGE_ATTRIBUTE))
                    .isEqualTo(AuthExceptionCase.NO_SESSION.getMessage());
            then(authClient).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("Bearer 헤더나 접두어 없는 이름의 쿠키는 인증에 쓰지 않는다")
        void ignoresBearerHeaderAndUnprefixedCookie() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/secured")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer some-jwt")
                            .cookie(new HttpCookie("session", "planted"))
                            .build());

            StepVerifier.create(repository.load(exchange))
                    .verifyComplete();

            then(authClient).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("살아 있는 세션 쿠키일 때")
    class WhenSessionIsValid {

        @Test
        void returnsSecurityContextWithAllRolesAsIndividualAuthorities() {
            given(authClient.validateSession(new ValidateSessionRequest("session-id", true)))
                    .willReturn(Mono.just(ApiResponse.success(
                            new ValidateSessionResponse("member-id", List.of("MEMBER", "ADMIN")))));

            StepVerifier.create(repository.load(withSessionCookie("session-id")))
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
        @DisplayName("X-Background-Request 요청은 touch=false로 확인한다")
        void backgroundRequestDoesNotTouchTheSession() {
            given(authClient.validateSession(new ValidateSessionRequest("session-id", false)))
                    .willReturn(Mono.just(ApiResponse.success(
                            new ValidateSessionResponse("member-id", List.of("MEMBER")))));
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/notifications/unread-count")
                            .header(CustomServerSecurityContextRepository.BACKGROUND_REQUEST_HEADER, "true")
                            .cookie(new HttpCookie(COOKIE_NAME, "session-id"))
                            .build());

            StepVerifier.create(repository.load(exchange))
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        void returnsEmptyWhenResponseDataIsNull() {
            given(authClient.validateSession(any(ValidateSessionRequest.class)))
                    .willReturn(Mono.just(ApiResponse.success()));

            StepVerifier.create(repository.load(withSessionCookie("session-with-null-data")))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("세션 확인이 WebClientResponseException을 던질 때")
    class WhenWebClientResponseExceptionOccurs {

        @Test
        void returnsEmptyAndSetsErrorAttributeFromResponseBody() throws Exception {
            String errorBody = "{\"status\":\"UNAUTHORIZED\",\"message\":\"로그인이 필요합니다.\"}";
            WebClientResponseException ex = WebClientResponseException.create(
                    HttpStatus.UNAUTHORIZED.value(), "Unauthorized", null,
                    errorBody.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            given(authClient.validateSession(any(ValidateSessionRequest.class))).willReturn(Mono.error(ex));
            given(objectMapper.readTree(errorBody)).willReturn(new ObjectMapper().readTree(errorBody));
            MockServerWebExchange exchange = withSessionCookie("expired-session");

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
            given(authClient.validateSession(any(ValidateSessionRequest.class)))
                    .willReturn(Mono.error(new RuntimeException("connection refused")));

            StepVerifier.create(repository.load(withSessionCookie("some-session")))
                    .verifyComplete();
        }
    }
}
