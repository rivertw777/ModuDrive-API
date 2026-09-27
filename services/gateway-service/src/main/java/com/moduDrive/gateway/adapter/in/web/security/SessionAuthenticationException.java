package com.moduDrive.gateway.adapter.in.web.security;

import com.moduDrive.gateway.exception.AuthExceptionCase;
import lombok.Getter;
import org.springframework.security.core.AuthenticationException;

/**
 * Carries the status/message the 401 body should show (auth-service's own, when it gave one), and
 * auth-service's {@code data} as-is — e.g. {@code reason: SESSION_REPLACED} (spec 004 1-2).
 */
@Getter
class SessionAuthenticationException extends AuthenticationException {

    private final String status;
    private final transient Object data;

    SessionAuthenticationException(String status, String message, Object data) {
        super(message);
        this.status = status;
        this.data = data;
    }

    SessionAuthenticationException(String status, String message) {
        this(status, message, null);
    }

    SessionAuthenticationException(AuthExceptionCase exceptionCase) {
        this(exceptionCase.getHttpStatus().name(), exceptionCase.getMessage());
    }
}
