package com.moduDrive.common.infrastructure.resilience4j;

import com.moduDrive.common.core.exception.BusinessException;
import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;


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
        else if (
                cause instanceof FeignException.GatewayTimeout ||
                        cause instanceof FeignException.ServiceUnavailable ||
                        cause instanceof FeignException.BadGateway ||
                        cause instanceof FeignException.TooManyRequests ||
                        cause instanceof RetryableException
        ) {
            throw new BusinessException(CircuitBreakerExceptionCase.SERVICE_UNAVAILABLE);
        }
        else if (cause instanceof FeignException) {
            throw (FeignException) cause;
        } else {
            throw new BusinessException(CircuitBreakerExceptionCase.SERVICE_UNAVAILABLE);
        }
    }

}
