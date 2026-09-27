package com.moduDrive.common.api.auth;

/** Session cookie name shared by auth-service (sets it) and the gateway (reads it). */
public final class SessionCookie {

    // __Host- makes the browser refuse the cookie unless it is Secure, Path=/ and host-only, so a
    // sibling subdomain can't plant or overwrite it. Local dev runs on http://localhost, which
    // Chrome treats as a secure origin, so the same name works there too.
    public static final String NAME = "__Host-session";

    private SessionCookie() {
    }
}
