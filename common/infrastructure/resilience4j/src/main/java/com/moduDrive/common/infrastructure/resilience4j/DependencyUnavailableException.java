package com.moduDrive.common.infrastructure.resilience4j;

import feign.FeignException;
import feign.Request;

import java.util.Collection;
import java.util.Map;

/** A 503 the other service marked {@code X-Dependency-Unavailable}: its dependency (S3) failed, not the
 * service. Still a {@link FeignException.ServiceUnavailable}, so it is answered like any 503; a circuit
 * that lists it in {@code ignore-exceptions} doesn't count it (spec 006 2-3-3). */
public class DependencyUnavailableException extends FeignException.ServiceUnavailable {

    public DependencyUnavailableException(String message, Request request, byte[] body,
                                          Map<String, Collection<String>> headers) {
        super(message, request, body, headers);
    }
}
