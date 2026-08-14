package com.moduDrive.common.api.dto.mail;

import java.util.UUID;

/** Published by member-service (topic {@link MailTopics#VERIFICATION_REQUESTED}) after a signup commits. */
public record VerificationMailRequested(UUID memberId, String email, String name, String verificationToken) {
}
