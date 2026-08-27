package com.moduDrive.gateway.adapter.in.web.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CsrfOriginGuardFilterTest {

    private static final String CLIENT_URL = "https://app.modudrive.com";

    @Mock
    private GatewayFilterChain chain;

    private final CsrfOriginGuardFilter filter = new CsrfOriginGuardFilter(CLIENT_URL);

    @Nested
    @DisplayName("허용된 origin에서 /logout, /reissue를 POST할 때")
    class WhenRequestFromAllowedOrigin {

        @Test
        void delegatesToChain() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/v1/auth/logout")
                            .header(HttpHeaders.ORIGIN, CLIENT_URL)
                            .build());
            given(chain.filter(exchange)).willReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            then(chain).should().filter(exchange);
        }
    }

    @Nested
    @DisplayName("다른 origin에서 /reissue를 POST할 때 (#205)")
    class WhenRequestFromDisallowedOrigin {

        @Test
        void rejectsWithForbiddenWithoutTouchingTheChain() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/v1/auth/reissue")
                            .header(HttpHeaders.ORIGIN, "https://attacker.example")
                            .build());

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            then(chain).should(never()).filter(exchange);
        }
    }

    @Nested
    @DisplayName("Origin이 없고 Referer만 있을 때")
    class WhenOriginIsMissingButRefererMatches {

        @Test
        void fallsBackToRefererAndDelegates() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/v1/auth/logout")
                            .header(HttpHeaders.REFERER, CLIENT_URL + "/settings")
                            .build());
            willReturn(Mono.empty()).given(chain).filter(exchange);

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            then(chain).should().filter(exchange);
        }
    }

    @Nested
    @DisplayName("Origin과 Referer가 둘 다 없을 때")
    class WhenBothOriginAndRefererAreMissing {

        @Test
        void rejectsWithForbidden() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/v1/auth/logout").build());

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("보호 대상이 아닌 라우트일 때")
    class WhenRouteIsNotProtected {

        @Test
        void delegatesRegardlessOfOrigin() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/v1/member/profile")
                            .header(HttpHeaders.ORIGIN, "https://attacker.example")
                            .build());
            given(chain.filter(exchange)).willReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            then(chain).should().filter(exchange);
        }

        @Test
        @DisplayName("같은 경로라도 GET이면 검사 대상이 아니다")
        void delegatesForGetOnAProtectedPath() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/auth/logout")
                            .header(HttpHeaders.ORIGIN, "https://attacker.example")
                            .build());
            given(chain.filter(exchange)).willReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            then(chain).should().filter(exchange);
        }
    }
}
