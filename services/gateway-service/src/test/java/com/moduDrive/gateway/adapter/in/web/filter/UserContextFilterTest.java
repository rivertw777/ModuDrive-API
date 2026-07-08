package com.moduDrive.gateway.adapter.in.web.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserContextFilterTest {

    @Mock
    private GatewayFilterChain chain;
    @InjectMocks
    private UserContextFilter filter;

    @Nested
    @DisplayName("인증된 사용자 요청일 때")
    class WhenUserIsAuthenticated {

        @Test
        void propagatesUserIdAndRolesAsHeaders() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/secured").build());

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    "member-id", null,
                    List.of(new SimpleGrantedAuthority("MEMBER"), new SimpleGrantedAuthority("ADMIN")));
            SecurityContextImpl securityContext = new SecurityContextImpl(auth);

            ArgumentCaptor<ServerWebExchange> captor = forClass(ServerWebExchange.class);
            given(chain.filter(captor.capture())).willReturn(Mono.empty());

            StepVerifier.create(
                    filter.filter(exchange, chain)
                            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)))
            ).verifyComplete();

            ServerWebExchange captured = captor.getValue();
            assertThat(captured.getRequest().getHeaders().getFirst(UserContextFilter.HEADER_USER_ID))
                    .isEqualTo("member-id");
            assertThat(captured.getRequest().getHeaders().getFirst(UserContextFilter.HEADER_USER_ROLE))
                    .isEqualTo("MEMBER,ADMIN");
        }
    }

    @Nested
    @DisplayName("인증되지 않은 요청일 때")
    class WhenUserIsNotAuthenticated {

        @Test
        void proceedsWithoutUserHeaders() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/auth/login").build());

            ArgumentCaptor<ServerWebExchange> captor = forClass(ServerWebExchange.class);
            given(chain.filter(captor.capture())).willReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            ServerWebExchange captured = captor.getValue();
            assertThat(captured.getRequest().getHeaders().getFirst(UserContextFilter.HEADER_USER_ID)).isNull();
            assertThat(captured.getRequest().getHeaders().getFirst(UserContextFilter.HEADER_USER_ROLE)).isNull();
        }

        @Test
        void stripsForgedUserContextHeadersFromClient() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/member/sign-up")
                            .header(UserContextFilter.HEADER_USER_ID, "forged-admin-id")
                            .header(UserContextFilter.HEADER_USER_ROLE, "ADMIN")
                            .build());

            ArgumentCaptor<ServerWebExchange> captor = forClass(ServerWebExchange.class);
            given(chain.filter(captor.capture())).willReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            ServerWebExchange captured = captor.getValue();
            assertThat(captured.getRequest().getHeaders().getFirst(UserContextFilter.HEADER_USER_ID)).isNull();
            assertThat(captured.getRequest().getHeaders().getFirst(UserContextFilter.HEADER_USER_ROLE)).isNull();
        }
    }

    @Nested
    @DisplayName("필터 순서 확인")
    class WhenCheckingOrder {

        @Test
        void hasOrderLowerThanLowestPrecedence() {
            assertThat(filter.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE - 1);
        }
    }
}
