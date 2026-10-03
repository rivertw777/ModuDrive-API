package com.moduDrive.common.infrastructure.resilience4j;

import com.moduDrive.common.core.exception.BusinessException;
import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;

import java.net.SocketTimeoutException;


@Slf4j
public class FeignFallbackUtils {

    private FeignFallbackUtils() {
    }

    public static <T> T handleFallback(Throwable cause) {
        // DEBUG: an open circuit sends every call here; the circuit/retry WARNs already tell the story.
        log.debug("Fallback triggered for {}", cause.getClass().getSimpleName());

        if (cause instanceof CallNotPermittedException) {
            throw new BusinessException(CircuitBreakerExceptionCase.SERVICE_IS_OPEN);
        }
        // Connect or read timeout (hc5's ConnectTimeoutException is a SocketTimeoutException too), or a
        // 504 from the service — same answer the gateway gives for a timeout.
        else if (cause instanceof RetryableException && cause.getCause() instanceof SocketTimeoutException
                || cause instanceof FeignException.GatewayTimeout) {
            throw new BusinessException(CircuitBreakerExceptionCase.CONNECTION_TIMEOUT);
        }
        else if (cause instanceof FeignException.ServiceUnavailable
                || cause instanceof FeignException.BadGateway
                || cause instanceof RetryableException) {
            throw new BusinessException(CircuitBreakerExceptionCase.SERVICE_UNAVAILABLE);
        }
        else if (cause instanceof FeignException) {
            throw (FeignException) cause;
        } else {
            throw new BusinessException(CircuitBreakerExceptionCase.SERVICE_UNAVAILABLE);
        }
    }

}
