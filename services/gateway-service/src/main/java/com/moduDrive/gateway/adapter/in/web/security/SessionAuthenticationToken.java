package com.moduDrive.gateway.adapter.in.web.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.List;

/** The session cookie before auth-service has confirmed it — never authenticated. */
class SessionAuthenticationToken extends AbstractAuthenticationToken {

    private final String sessionId;
    private final boolean touch;

    SessionAuthenticationToken(String sessionId, boolean touch) {
        super(List.of());
        this.sessionId = sessionId;
        this.touch = touch;
    }

    @Override
    public String getCredentials() {
        return sessionId;
    }

    @Override
    public Object getPrincipal() {
        return null;
    }

    boolean touch() {
        return touch;
    }
}
