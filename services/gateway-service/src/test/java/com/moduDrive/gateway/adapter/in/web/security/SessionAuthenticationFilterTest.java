package com.moduDrive.gateway.adapter.in.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.api.dto.auth.ValidateSessionRequest;
import com.moduDrive.common.api.dto.auth.ValidateSessionResponse;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.gateway.adapter.out.client.auth.AuthClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

/** The converter, manager and failure handler assembled the way SecurityConfig does it. */
@ExtendWith(MockitoExtension.class)
class SessionAuthenticationFilterTest {

    @Mock
    private AuthClient authClient;

    private AuthenticationWebFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthenticationWebFilter(new SessionAuthenticationManager(authClient, new ObjectMapper()));
        filter.setServerAuthenticationConverter(new SessionAuthenticationConverter(true));
        filter.setAuthenticationFailureHandler(new SessionAuthenticationFailureHandler());
    }

    private static MockServerWebExchange withSessionCookie() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/secured")
                .cookie(new HttpCookie("__Host-session", "session-id")).build());
    }

    @Nested
    @DisplayName("살아 있는 세션으로 요청할 때")
    class WhenSessionIsValid {

        @Test
        @DisplayName("뒤 필터들이 인증 정보를 여러 번 꺼내도 세션 확인은 1번이다")
        void validatesSessionOncePerRequest() {
            given(authClient.validateSession(any(ValidateSessionRequest.class)))
                    .willReturn(Mono.just(ApiResponse.success(new ValidateSessionResponse("member-id", List.of("MEMBER")))));
            List<Object> principals = new ArrayList<>();
            WebFilterChain chain = exchange -> ReactiveSecurityContextHolder.getContext()
                    .doOnNext(ctx -> principals.add(ctx.getAuthentication().getPrincipal()))
                    .then(ReactiveSecurityContextHolder.getContext())
                    .doOnNext(ctx -> principals.add(ctx.getAuthentication().getPrincipal()))
                    .then();

            StepVerifier.create(filter.filter(withSessionCookie(), chain)).verifyComplete();

            assertThat(principals).containsExactly("member-id", "member-id");
            then(authClient).should(times(1)).validateSession(any(ValidateSessionRequest.class));
        }
    }

    @Nested
    @DisplayName("세션이 죽어 있을 때")
    class WhenSessionIsDead {

        @Test
        @DisplayName("요청을 끊지 않고 익명으로 계속 보내며 에러를 기록한다")
        void continuesAnonymouslyAndRecordsError() {
            given(authClient.validateSession(any(ValidateSessionRequest.class)))
                    .willReturn(Mono.just(ApiResponse.success()));
            MockServerWebExchange exchange = withSessionCookie();
            List<SecurityContext> contexts = new ArrayList<>();
            boolean[] reachedChain = {false};
            WebFilterChain chain = ex -> {
                reachedChain[0] = true;
                return ReactiveSecurityContextHolder.getContext().doOnNext(contexts::add).then();
            };

            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

            assertThat(reachedChain[0]).isTrue();
            assertThat(contexts).isEmpty();
            assertThat(exchange.getAttributes().get(AuthErrorAttributeUtils.STATUS_ATTRIBUTE)).isEqualTo("UNAUTHORIZED");
        }
    }
}
