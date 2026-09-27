package com.moduDrive.common.event.auth;

/** Queues auth-service publishes to. The queue name as the broker knows it. */
public final class AuthQueues {

    public static final String LOGIN_VERIFICATION_MAIL_REQUESTED = "mail-login-verification-requested";

    private AuthQueues() {
    }
}
