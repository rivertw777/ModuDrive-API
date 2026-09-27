package com.moduDrive.common.api.auth;

import java.util.Set;

/** Auth cookie names shared by auth-service (sets them) and the gateway (reads them, and keeps them
 * away from every other service). */
public final class SessionCookie {

    // __Host- makes the browser refuse the cookie unless it is Secure, Path=/ and host-only, so a
    // sibling subdomain can't plant or overwrite it. Local dev runs on http://localhost, which
    // Chrome treats as a secure origin, so the same name works there too.
    public static final String NAME = "__Host-session";
    /** Long-lived id of the browser, so a login from a device already verified by email skips the code (spec 004 2-1). */
    public static final String DEVICE_NAME = "__Host-device";
    /** Ties the code the user types to the login that asked for it (spec 004 2-1). */
    public static final String LOGIN_CHALLENGE_NAME = "__Host-login-challenge";

    /** Only auth-service reads these; the gateway strips them from requests to anything else. */
    public static final Set<String> AUTH_ONLY_NAMES = Set.of(NAME, DEVICE_NAME, LOGIN_CHALLENGE_NAME);

    private SessionCookie() {
    }
}
