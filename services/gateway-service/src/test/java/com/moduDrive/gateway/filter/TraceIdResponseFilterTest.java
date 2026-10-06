package com.moduDrive.gateway.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.handler.TracingObservationHandler.TracingContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class TraceIdResponseFilterTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    @Mock
    private WebFilterChain chain;

    private final TraceIdResponseFilter filter = new TraceIdResponseFilter();

    private static MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/files").build());
    }

    private static void observe(MockServerWebExchange exchange) {
        TraceContext traceContext = mock(TraceContext.class);
        given(traceContext.traceId()).willReturn(TRACE_ID);
        Span span = mock(Span.class);
        given(span.context()).willReturn(traceContext);
        TracingContext tracingContext = new TracingContext();
        tracingContext.setSpan(span);

        ServerRequestObservationContext observation = new ServerRequestObservationContext(
                exchange.getRequest(), exchange.getResponse(), exchange.getAttributes());
        observation.put(TracingContext.class, tracingContext);
        exchange.getAttributes().put(ServerRequestObservationContext.CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE, observation);
    }

    // The response is committed by whoever writes it (a route, the entry point, a filter refusing it).
    private void runAndCommit(MockServerWebExchange exchange) {
        given(chain.filter(exchange)).willReturn(Mono.defer(() -> exchange.getResponse().setComplete()));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    }

    @Nested
    @DisplayName("요청이 추적되고 있을 때")
    class WhenTraced {

        @Test
        @DisplayName("응답에 trace id 헤더를 붙인다")
        void setsTraceIdHeader() {
            MockServerWebExchange exchange = exchange();
            observe(exchange);

            runAndCommit(exchange);

            assertThat(exchange.getResponse().getHeaders().getFirst(TraceIdResponseFilter.HEADER_TRACE_ID)).isEqualTo(TRACE_ID);
        }
    }

    @Nested
    @DisplayName("추적 정보가 없을 때")
    class WhenNotTraced {

        @Test
        @DisplayName("헤더 없이 응답한다")
        void leavesHeaderOut() {
            MockServerWebExchange exchange = exchange();

            runAndCommit(exchange);

            assertThat(exchange.getResponse().getHeaders().containsHeader(TraceIdResponseFilter.HEADER_TRACE_ID)).isFalse();
        }
    }
}
