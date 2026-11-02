package com.moduDrive.gateway.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LogoutSessionEvictionFilterTest {

    private final SessionCheckCache cache = new SessionCheckCache();
    private final LogoutSessionEvictionFilter filter = new LogoutSessionEvictionFilter(cache);
    private final WebFilterChain chain = exchange -> Mono.empty();
    private final AtomicInteger checks = new AtomicInteger();

    private final Mono<Authentication> check = Mono.fromSupplier(() -> {
        checks.incrementAndGet();
        return UsernamePasswordAuthenticationToken.authenticated("member-id", null, List.of());
    });

    private void cacheSession() {
        StepVerifier.create(cache.get("session-id", check)).expectNextCount(1).verifyComplete();
    }

    private void checkAgain() {
        StepVerifier.create(cache.get("session-id", check)).expectNextCount(1).verifyComplete();
    }

    private static MockServerWebExchange request(MockServerHttpRequest.BaseBuilder<?> builder) {
        return MockServerWebExchange.from(builder.cookie(new HttpCookie("__Host-session", "session-id")).build());
    }

    @Nested
    @DisplayName("로그아웃 요청이 지나갔을 때")
    class WhenLogoutPasses {

        @Test
        @DisplayName("그 세션의 저장된 확인을 지워 다음 요청은 다시 확인한다")
        void evictsSession() {
            cacheSession();

            StepVerifier.create(filter.filter(request(MockServerHttpRequest.post("/api/v1/auth/logout")), chain))
                    .verifyComplete();
            checkAgain();

            assertThat(checks).hasValue(2);
        }
    }

    @Nested
    @DisplayName("로그아웃이 아닌 요청일 때")
    class WhenOtherRequest {

        @Test
        @DisplayName("저장된 확인을 그대로 둔다")
        void keepsSession() {
            cacheSession();

            StepVerifier.create(filter.filter(request(MockServerHttpRequest.get("/api/v1/auth/logout")), chain))
                    .verifyComplete();
            StepVerifier.create(filter.filter(request(MockServerHttpRequest.post("/api/v1/files")), chain))
                    .verifyComplete();
            checkAgain();

            assertThat(checks).hasValue(1);
        }
    }
}
