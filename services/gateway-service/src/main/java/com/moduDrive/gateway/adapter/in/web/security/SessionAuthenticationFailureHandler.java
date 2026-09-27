package com.moduDrive.gateway.adapter.in.web.security;

import com.moduDrive.gateway.exception.AuthExceptionCase;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * A dead session doesn't end the request here: it carries on anonymously, so permitAll paths still
 * work and protected ones are refused by authorization — whose 401 body shows the error kept here.
 */
@Component
class SessionAuthenticationFailureHandler implements ServerAuthenticationFailureHandler {

    @Override
    public Mono<Void> onAuthenticationFailure(WebFilterExchange webFilterExchange, AuthenticationException exception) {
        if (exception instanceof SessionAuthenticationException e) {
            AuthErrorAttributeUtils.setAuthErrorAttribute(webFilterExchange.getExchange(), e.getStatus(), e.getMessage());
        } else {
            AuthErrorAttributeUtils.setAuthErrorAttribute(webFilterExchange.getExchange(), AuthExceptionCase.UNAUTHORIZED);
        }
        return webFilterExchange.getChain().filter(webFilterExchange.getExchange());
    }
}
