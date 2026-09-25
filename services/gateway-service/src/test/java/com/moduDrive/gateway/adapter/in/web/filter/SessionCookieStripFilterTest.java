package com.moduDrive.gateway.adapter.in.web.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCookieStripFilterTest {

    private final SessionCookieStripFilter filter = new SessionCookieStripFilter();

    private String forwardedCookieHeader(MockServerHttpRequest request) {
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(MockServerWebExchange.from(request), chain)).verifyComplete();

        return forwarded.get().getRequest().getHeaders().getFirst(HttpHeaders.COOKIE);
    }

    @Nested
    @DisplayName("auth-service가 아닌 서비스로 가는 요청일 때")
    class WhenRoutedElsewhere {

        @Test
        @DisplayName("세션 쿠키만 빼고 나머지 쿠키는 남긴다")
        void stripsOnlyTheSessionCookie() {
            String cookieHeader = forwardedCookieHeader(MockServerHttpRequest.get("/api/v1/files")
                    .cookie(new HttpCookie("__Host-session", "secret"), new HttpCookie("theme", "dark"))
                    .build());

            assertThat(cookieHeader).isEqualTo("theme=dark");
        }

        @Test
        void dropsCookieHeaderWhenNothingElseRemains() {
            String cookieHeader = forwardedCookieHeader(MockServerHttpRequest.get("/api/v1/storage/view/id")
                    .cookie(new HttpCookie("session", "secret"))
                    .build());

            assertThat(cookieHeader).isNull();
        }
    }

    @Nested
    @DisplayName("auth-service로 가는 요청일 때")
    class WhenRoutedToAuthService {

        @Test
        @DisplayName("로그인·로그아웃에 필요하므로 그대로 넘긴다")
        void keepsTheSessionCookie() {
            String cookieHeader = forwardedCookieHeader(MockServerHttpRequest.post("/api/v1/auth/logout")
                    .cookie(new HttpCookie("__Host-session", "secret"))
                    .build());

            assertThat(cookieHeader).isEqualTo("__Host-session=secret");
        }
    }
}
