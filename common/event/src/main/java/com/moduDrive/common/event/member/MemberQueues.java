package com.moduDrive.common.event.member;

/** Queues member-service publishes to. The queue name as the broker knows it. */
public final class MemberQueues {

    public static final String SIGNED_UP = "member-signed-up";
    public static final String SIGN_UP_VERIFICATION_MAIL_REQUESTED = "mail-verification-requested";
    public static final String PASSWORD_CHANGED = "member-password-changed";

    private MemberQueues() {
    }
}
