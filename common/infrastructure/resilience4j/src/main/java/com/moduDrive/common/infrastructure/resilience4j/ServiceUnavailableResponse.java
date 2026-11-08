package com.moduDrive.common.infrastructure.resilience4j;

import feign.RetryableException;

import java.util.function.Predicate;

/**
 * A 503 the other service answered itself. Every 503 of ours carries {@code Retry-After}, and with it
 * Feign's default decoder throws {@link RetryableException}, not {@code FeignException.ServiceUnavailable}.
 * A 503 without one — a proxy in between finding no healthy instance — stays a ServiceUnavailable and is
 * not matched, nor is a connect failure (a RetryableException with no status, -1).
 */
public class ServiceUnavailableResponse implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        return throwable instanceof RetryableException e && e.status() == 503;
    }
}
