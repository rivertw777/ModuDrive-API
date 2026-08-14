package com.moduDrive.common.event.mail;

/** Published by member-service (topic {@link MailTopics#VERIFICATION_REQUESTED}) when a signup email is requested, before the member exists. */
public record VerificationMailRequested(String email, String verificationToken) {
}
