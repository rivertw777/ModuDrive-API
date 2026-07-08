package com.moduDrive.gateway.adapter.in.web.fallback;

import com.moduDrive.common.infrastructure.resilience4j.CircuitBreakerExceptionCase;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackControllerTest {

    private final FallbackController controller = new FallbackController();

    @Nested
    @DisplayName("TimeoutException으로 fallback이 트리거됐을 때")
    class WhenTimeoutExceptionOccurs {

        @Test
        void returnsGatewayTimeoutResponse() {
            MockServerWebExchange exchange = exchangeWithException(new TimeoutException("timeout"));

            StepVerifier.create(controller.defaultFallback(exchange))
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                        assertThat(response.getBody().getMessage())
                                .isEqualTo(CircuitBreakerExceptionCase.CONNECTION_TIMEOUT.getMessage());
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("CallNotPermittedException으로 fallback이 트리거됐을 때")
    class WhenCallNotPermittedExceptionOccurs {

        @Test
        void returnsServiceUnavailableWithOpenCircuitMessage() {
            CallNotPermittedException ex = CallNotPermittedException.createCallNotPermittedException(
                    CircuitBreaker.ofDefaults("test"));
            MockServerWebExchange exchange = exchangeWithException(ex);

            StepVerifier.create(controller.defaultFallback(exchange))
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(response.getBody().getMessage())
                                .isEqualTo(CircuitBreakerExceptionCase.SERVICE_IS_OPEN.getMessage());
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("그 외 예외로 fallback이 트리거됐을 때")
    class WhenGenericExceptionOccurs {

        @Test
        void returnsServiceUnavailableResponse() {
            MockServerWebExchange exchange = exchangeWithException(new RuntimeException("connection refused"));

            StepVerifier.create(controller.defaultFallback(exchange))
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(response.getBody().getMessage())
                                .isEqualTo(CircuitBreakerExceptionCase.SERVICE_UNAVAILABLE.getMessage());
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("예외 속성이 없을 때")
    class WhenNoExceptionAttribute {

        @Test
        void returnsEmpty() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/fallback/default").build());

            StepVerifier.create(controller.defaultFallback(exchange))
                    .verifyComplete();
        }
    }

    private MockServerWebExchange exchangeWithException(Throwable ex) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/fallback/default").build());
        exchange.getAttributes().put(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR, ex);
        return exchange;
    }
}
