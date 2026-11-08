package com.moduDrive.gateway.config;

import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.gateway.config.UnmarkedServiceUnavailableFilter.ServiceUnavailableResponseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UnmarkedServiceUnavailableFilterTest {

    private final UnmarkedServiceUnavailableFilter filter = new UnmarkedServiceUnavailableFilter();

    /** The route answering with this status (and the dependency mark, when asked). */
    private static MockServerWebExchange answered(HttpStatus status, boolean marked) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/storage/x").build());
        exchange.getResponse().setStatusCode(status);
        if (marked) {
            exchange.getResponse().getHeaders().set(GlobalExceptionHandler.DEPENDENCY_UNAVAILABLE_HEADER, "true");
        }
        return exchange;
    }

    private static final GatewayFilterChain ROUTE = exchange -> Mono.empty();

    @Test
    @DisplayName("서비스가 표시 없는 503으로 답하면 서킷이 세도록 실패로 바꾼다")
    void failsAnUnmarked503() {
        StepVerifier.create(filter.filter(answered(HttpStatus.SERVICE_UNAVAILABLE, false), ROUTE))
                .verifyError(ServiceUnavailableResponseException.class);
    }

    @Test
    @DisplayName("의존 대상(S3) 표시가 붙은 503은 서비스의 응답 그대로 둔다")
    void passesAMarked503On() {
        StepVerifier.create(filter.filter(answered(HttpStatus.SERVICE_UNAVAILABLE, true), ROUTE))
                .verifyComplete();
    }

    @Test
    @DisplayName("503이 아닌 응답은 그대로 둔다")
    void passesOtherStatusesOn() {
        StepVerifier.create(filter.filter(answered(HttpStatus.OK, false), ROUTE)).verifyComplete();
        StepVerifier.create(filter.filter(answered(HttpStatus.INTERNAL_SERVER_ERROR, false), ROUTE)).verifyComplete();
    }
}
