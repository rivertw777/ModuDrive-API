package com.moduDrive.common.event.mail;

/**
 * Published by member-service (queue {@link MailQueues#VERIFICATION_REQUESTED}) when a signup email
 * is requested, before the member exists, or when a login from a new device needs its code (auth spec
 * 004 2-1). {@code purpose} picks the mail's wording; null (a message sent before it existed) is a
 * signup.
 */
public record VerificationMailRequested(String email, String verificationCode, Purpose purpose) {

    public enum Purpose { SIGN_UP, LOGIN }
}
