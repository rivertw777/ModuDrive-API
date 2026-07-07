package com.moduDrive.gateway.adapter.in.web.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PreFilterTest {

    @Mock
    private GatewayFilterChain chain;
    @InjectMocks
    private PreFilter preFilter;

    @Nested
    @DisplayName("요청이 들어올 때")
    class WhenRequestArrives {

        @Test
        void delegatesToChain() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/v1/member/profile").build());
            given(chain.filter(exchange)).willReturn(Mono.empty());

            StepVerifier.create(preFilter.filter(exchange, chain))
                    .verifyComplete();

            then(chain).should().filter(exchange);
        }

        @Test
        void hasHighestPrecedenceOrder() {
            assertThat(preFilter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        }
    }
}
