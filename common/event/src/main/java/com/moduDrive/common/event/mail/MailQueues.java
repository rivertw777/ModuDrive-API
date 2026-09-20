package com.moduDrive.common.event.mail;

/** Queue names shared by mail event producers (member, file) and the consumer (mail-service).
 * Logical names, without a broker's own naming rules — the adapter maps them (SQS appends {@code .fifo}). */
public final class MailQueues {

    public static final String VERIFICATION_REQUESTED = "mail-verification-requested";
    public static final String SHARE_INVITE_REQUESTED = "mail-share-invite-requested";

    private MailQueues() {
    }
}
