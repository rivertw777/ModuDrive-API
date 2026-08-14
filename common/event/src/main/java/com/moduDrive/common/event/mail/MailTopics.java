package com.moduDrive.common.event.mail;

/** Kafka topic names shared by mail event producers (member, file) and the consumer (mail-service). */
public final class MailTopics {

    public static final String VERIFICATION_REQUESTED = "mail.verification-requested";
    public static final String SHARE_INVITE_REQUESTED = "mail.share-invite-requested";

    private MailTopics() {
    }
}
