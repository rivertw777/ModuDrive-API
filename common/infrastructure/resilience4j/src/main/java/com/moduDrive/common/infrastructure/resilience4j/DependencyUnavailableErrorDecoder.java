package com.moduDrive.common.infrastructure.resilience4j;

import com.moduDrive.common.core.web.GlobalExceptionHandler;
import feign.Response;
import feign.codec.ErrorDecoder;

import java.io.IOException;

/** Every Feign client's decoder: a 503 marked {@code X-Dependency-Unavailable} becomes a
 * {@link DependencyUnavailableException}; anything else is decoded as Feign does by default. Without it
 * Feign turns our 503s, which carry {@code Retry-After}, into a {@code RetryableException} that drops
 * the headers. */
public class DependencyUnavailableErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder fallback = new ErrorDecoder.Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() == 503 && response.headers().containsKey(GlobalExceptionHandler.DEPENDENCY_UNAVAILABLE_HEADER)) {
            byte[] body = new byte[0];
            try {
                if (response.body() != null) {
                    body = response.body().asInputStream().readAllBytes();
                }
            } catch (IOException ignored) {
                // The body is only the message passed on to the fallback; the status is what matters.
            }
            return new DependencyUnavailableException("[503] " + methodKey, response.request(), body, response.headers());
        }
        return fallback.decode(methodKey, response);
    }
}
