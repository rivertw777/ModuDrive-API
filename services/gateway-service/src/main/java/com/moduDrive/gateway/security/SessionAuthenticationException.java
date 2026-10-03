package com.moduDrive.gateway.security;

import com.moduDrive.common.core.exception.ExceptionCase;
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

    SessionAuthenticationException(ExceptionCase exceptionCase) {
        this(exceptionCase.getHttpStatus().name(), exceptionCase.getMessage());
    }
}
