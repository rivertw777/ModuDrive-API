package com.moduDrive.gateway.adapter.in.web.security;

import com.moduDrive.common.api.dto.auth.SessionCookie;
import com.moduDrive.gateway.exception.AuthExceptionCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Reads the session cookie into a token; no cookie means the request carries on anonymously. */
@Component
class SessionAuthenticationConverter implements ServerAuthenticationConverter {

    /** Sent by the WEB on polling requests: check the session without restarting its idle timeout. */
    static final String BACKGROUND_REQUEST_HEADER = "X-Background-Request";

    private final String sessionCookieName;

    SessionAuthenticationConverter(@Value("${session.cookie.secure}") boolean secureSessionCookie) {
        this.sessionCookieName = SessionCookie.name(secureSessionCookie);
    }

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(sessionCookieName);
        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            AuthErrorAttributeUtils.setAuthErrorAttribute(exchange, AuthExceptionCase.NO_SESSION);
            return Mono.empty();
        }
        // A client can only use this header to NOT extend its own session, so it is safe to trust.
        boolean touch = !"true".equalsIgnoreCase(
                exchange.getRequest().getHeaders().getFirst(BACKGROUND_REQUEST_HEADER));
        return Mono.just(new SessionAuthenticationToken(sessionCookie.getValue(), touch));
    }
}
