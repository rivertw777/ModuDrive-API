package com.moduDrive.common.core.exception;

import org.springframework.http.HttpStatus;

public interface ExceptionCase {
    HttpStatus getHttpStatus();
    String getMessage();

    /** A 503 caused by something this service depends on (S3), not by the service itself. It is
     * answered with {@code X-Dependency-Unavailable}, so callers' circuits don't count it (spec 006 2-3-3). */
    default boolean isDependencyUnavailable() {
        return false;
    }
}
