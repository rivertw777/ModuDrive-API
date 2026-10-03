package com.moduDrive.common.infrastructure.resilience4j;

import feign.FeignException;
import feign.RetryableException;
import org.apache.hc.client5.http.ConnectTimeoutException;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.function.Predicate;

/**
 * Retries only calls that never reached the other service (and 503s): the request was never sent, so
 * resending is safe, and three attempts (3s connect timeout each) still fit inside the gateway's 15s.
 * A read timeout means the service took the request and is slow — resending only stacks more work on
 * it, so it isn't retried. Feign wraps every I/O error in RetryableException, hence the look at the cause.
 */
public class RetryOnConnectFailure implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        if (throwable instanceof FeignException.ServiceUnavailable) {
            return true;
        }
        if (!(throwable instanceof RetryableException)) {
            return false;
        }
        Throwable cause = throwable.getCause();
        // HttpClient 5 throws HttpHostConnectException (a ConnectException) on refusal and its own
        // ConnectTimeoutException on a connect timeout — a read timeout stays a SocketTimeoutException.
        return cause instanceof ConnectException
                || cause instanceof ConnectTimeoutException
                || cause instanceof NoRouteToHostException
                || cause instanceof UnknownHostException;
    }
}
