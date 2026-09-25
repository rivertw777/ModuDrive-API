package com.moduDrive.gateway.adapter.in.web.security;

import com.moduDrive.gateway.exception.AuthExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class SessionAuthenticationConverterTest {

    private static final String COOKIE_NAME = "__Host-session";

    private final SessionAuthenticationConverter converter = new SessionAuthenticationConverter(true);

    @Nested
    @DisplayName("세션 쿠키가 없을 때")
    class WhenSessionCookieIsMissing {

        @Test
        void returnsEmptyAndSetsNoSessionAttribute() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/secured").build());

            StepVerifier.create(converter.convert(exchange))
                    .verifyComplete();

            assertThat(exchange.getAttributes().get(AuthErrorAttributeUtils.MESSAGE_ATTRIBUTE))
                    .isEqualTo(AuthExceptionCase.NO_SESSION.getMessage());
        }

        @Test
        @DisplayName("접두어 없는 이름의 쿠키는 인증에 쓰지 않는다")
        void ignoresUnprefixedCookie() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/secured").cookie(new HttpCookie("session", "planted")).build());

            StepVerifier.create(converter.convert(exchange))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("세션 쿠키가 있을 때")
    class WhenSessionCookieIsPresent {

        @Test
        void returnsTokenThatTouchesTheSession() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/secured").cookie(new HttpCookie(COOKIE_NAME, "session-id")).build());

            StepVerifier.create(converter.convert(exchange))
                    .assertNext(auth -> {
                        SessionAuthenticationToken token = (SessionAuthenticationToken) auth;
                        assertThat(token.getCredentials()).isEqualTo("session-id");
                        assertThat(token.touch()).isTrue();
                        assertThat(token.isAuthenticated()).isFalse();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("X-Background-Request 요청은 touch=false")
        void backgroundRequestDoesNotTouchTheSession() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/notifications/unread-count")
                            .header(SessionAuthenticationConverter.BACKGROUND_REQUEST_HEADER, "true")
                            .cookie(new HttpCookie(COOKIE_NAME, "session-id"))
                            .build());

            StepVerifier.create(converter.convert(exchange))
                    .assertNext(auth -> assertThat(((SessionAuthenticationToken) auth).touch()).isFalse())
                    .verifyComplete();
        }
    }
}
