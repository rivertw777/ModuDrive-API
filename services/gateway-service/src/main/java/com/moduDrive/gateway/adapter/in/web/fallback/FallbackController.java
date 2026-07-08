package com.moduDrive.gateway.adapter.in.web.fallback;

import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.common.infrastructure.resilience4j.CircuitBreakerExceptionCase;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeoutException;

@Slf4j
@RestController
class FallbackController {

    @RequestMapping(value = "/fallback/default", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public Mono<ResponseEntity<ApiResponse<Object>>> defaultFallback(ServerWebExchange exchange) {
        Throwable exception = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);

        return Mono.justOrEmpty(exception)
                .doOnNext(ex -> log.error("Fallback triggered for {}", ex.getClass().getSimpleName()))
                .map(this::handleCircuitBreakerException);
    }

    private ResponseEntity<ApiResponse<Object>> handleCircuitBreakerException(Throwable ex) {
        if (ex instanceof TimeoutException) {
            return createErrorResponse(CircuitBreakerExceptionCase.CONNECTION_TIMEOUT);
        } else if (ex instanceof CallNotPermittedException) {
            return createErrorResponse(CircuitBreakerExceptionCase.SERVICE_IS_OPEN);
        } else {
            return createErrorResponse(CircuitBreakerExceptionCase.SERVICE_UNAVAILABLE);
        }
    }

    private ResponseEntity<ApiResponse<Object>> createErrorResponse(CircuitBreakerExceptionCase exceptionCase) {
        return ResponseEntity.status(exceptionCase.getHttpStatus())
                .body(ApiResponse.error(exceptionCase));
    }
}
