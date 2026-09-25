package com.moduDrive.gateway.adapter.in.web.security;

import com.moduDrive.gateway.exception.AuthExceptionCase;
import lombok.Getter;
import org.springframework.security.core.AuthenticationException;

/** Carries the status/message the 401 body should show (auth-service's own, when it gave one). */
@Getter
class SessionAuthenticationException extends AuthenticationException {

    private final String status;

    SessionAuthenticationException(String status, String message) {
        super(message);
        this.status = status;
    }

    SessionAuthenticationException(AuthExceptionCase exceptionCase) {
        this(exceptionCase.getHttpStatus().name(), exceptionCase.getMessage());
    }
}
