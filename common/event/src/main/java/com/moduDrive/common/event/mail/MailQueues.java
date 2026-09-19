package com.moduDrive.common.event.mail;

/** SQS FIFO queue names shared by mail event producers (member, file) and the consumer (mail-service). */
public final class MailQueues {

    public static final String VERIFICATION_REQUESTED = "mail-verification-requested.fifo";
    public static final String SHARE_INVITE_REQUESTED = "mail-share-invite-requested.fifo";

    private MailQueues() {
    }
}
