package com.moduDrive.common.api.dto.auth;

/**
 * Session cookie name shared by auth-service (sets it) and the gateway (reads it). Both derive it
 * from the same {@code SESSION_COOKIE_SECURE} flag, so they can never disagree.
 */
public final class SessionCookie {

    // __Host- makes the browser refuse the cookie unless it is Secure, Path=/ and host-only, so a
    // sibling subdomain can't plant or overwrite it. The prefix demands Secure, so plain-http
    // local dev falls back to an unprefixed name.
    public static final String SECURE_NAME = "__Host-session";
    public static final String INSECURE_NAME = "session";

    private SessionCookie() {
    }

    public static String name(boolean secure) {
        return secure ? SECURE_NAME : INSECURE_NAME;
    }
}
