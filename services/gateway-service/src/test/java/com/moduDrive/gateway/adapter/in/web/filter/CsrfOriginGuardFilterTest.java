package com.moduDrive.gateway.adapter.in.web.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CsrfOriginGuardFilterTest {

    private static final String CLIENT_URL = "https://app.modudrive.com";

    @Mock
    private WebFilterChain chain;

    private final CsrfOriginGuardFilter filter = new CsrfOriginGuardFilter(CLIENT_URL);

    private static MockServerWebExchange exchange(HttpMethod method, String path, String header, String value) {
        MockServerHttpRequest.BodyBuilder builder = MockServerHttpRequest.method(method, path);
        if (header != null) {
            builder.header(header, value);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private void assertPassed(MockServerWebExchange exchange) {
        given(chain.filter(exchange)).willReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        then(chain).should().filter(exchange);
    }

    private void assertForbidden(MockServerWebExchange exchange) {
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        then(chain).should(never()).filter(exchange);
    }

    @Nested
    @DisplayName("허용된 Origin에서 보낸 변경 요청일 때")
    class WhenFromAllowedOrigin {

        @Test
        void delegatesForEveryStateChangingMethod() {
            for (HttpMethod method : new HttpMethod[]{HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE}) {
                assertPassed(exchange(method, "/api/v1/files/some-id", HttpHeaders.ORIGIN, CLIENT_URL));
            }
        }
    }

    @Nested
    @DisplayName("다른 Origin에서 보낸 변경 요청일 때")
    class WhenFromDisallowedOrigin {

        @Test
        @DisplayName("경로와 상관없이 403 — 로그인도 막는다 (로그인 CSRF)")
        void rejectsOnAnyPathIncludingLogin() {
            assertForbidden(exchange(HttpMethod.POST, "/api/v1/auth/login", HttpHeaders.ORIGIN, "https://attacker.example"));
            assertForbidden(exchange(HttpMethod.DELETE, "/api/v1/files/some-id", HttpHeaders.ORIGIN, "https://attacker.example"));
        }
    }

    @Nested
    @DisplayName("Origin 없이 Referer만 있을 때")
    class WhenOnlyRefererIsPresent {

        @Test
        void acceptsRefererUnderClientUrl() {
            assertPassed(exchange(HttpMethod.POST, "/api/v1/auth/logout", HttpHeaders.REFERER, CLIENT_URL + "/settings"));
        }

        @Test
        @DisplayName("CLIENT_URL로 시작하는 다른 호스트는 막는다")
        void rejectsLookalikeHost() {
            assertForbidden(exchange(HttpMethod.POST, "/api/v1/auth/logout", HttpHeaders.REFERER, CLIENT_URL + ".attacker.example/x"));
        }
    }

    @Nested
    @DisplayName("Origin과 Referer가 둘 다 없는 변경 요청일 때")
    class WhenBothAreMissing {

        @Test
        void rejectsWithForbidden() {
            assertForbidden(exchange(HttpMethod.POST, "/api/v1/storage/archive", null, null));
        }
    }

    @Nested
    @DisplayName("GET 요청일 때")
    class WhenMethodIsSafe {

        @Test
        void delegatesRegardlessOfOrigin() {
            assertPassed(exchange(HttpMethod.GET, "/api/v1/files", HttpHeaders.ORIGIN, "https://attacker.example"));
        }
    }
}
