package com.moduDrive.gateway.adapter.in.web.security;

import com.moduDrive.common.core.exception.ExceptionCase;
import com.moduDrive.gateway.exception.AuthExceptionCase;
import org.springframework.web.server.ServerWebExchange;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

class AuthErrorAttributeUtils {

    static final String STATUS_ATTRIBUTE = "AUTH_ERROR_RESPONSE_STATUS";
    static final String MESSAGE_ATTRIBUTE = "AUTH_ERROR_RESPONSE_MESSAGE";

    private AuthErrorAttributeUtils() {
    }

    static void setAuthErrorAttribute(ServerWebExchange exchange, ExceptionCase exceptionCase) {
        exchange.getAttributes().put(STATUS_ATTRIBUTE, exceptionCase.getHttpStatus().name());
        exchange.getAttributes().put(MESSAGE_ATTRIBUTE, exceptionCase.getMessage());
    }

    static void setAuthErrorAttribute(ServerWebExchange exchange, String status, String message) {
        exchange.getAttributes().put(STATUS_ATTRIBUTE, status);
        exchange.getAttributes().put(MESSAGE_ATTRIBUTE, message);
    }

    static Tuple2<String, String> getAuthErrorAttribute(ServerWebExchange exchange) {
        String status = exchange.getAttributeOrDefault(STATUS_ATTRIBUTE, AuthExceptionCase.UNAUTHORIZED.getHttpStatus().name());
        String message = exchange.getAttributeOrDefault(MESSAGE_ATTRIBUTE, AuthExceptionCase.UNAUTHORIZED.getMessage());
        return Tuples.of(status, message);
    }
}
