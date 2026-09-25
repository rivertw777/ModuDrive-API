package com.moduDrive.auth.domain.model;

import java.time.Duration;

/** Session lifetime rules (spec 004 1-1-4) — constants, not config, so they can't drift per env. */
public final class SessionPolicy {

    /** Expires after this long without a user-initiated request. */
    public static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);

    /** Expires this long after login no matter how active the session is. */
    public static final Duration ABSOLUTE_TIMEOUT = Duration.ofHours(12);

    private SessionPolicy() {
    }
}
