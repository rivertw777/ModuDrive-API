package com.moduDrive.common.event.member;

/** Published by member-service (queue {@link MemberQueues#SIGN_UP_VERIFICATION_MAIL_REQUESTED}) when a
 * signup email is requested, before the member exists. */
public record SignUpVerificationMailRequested(String email, String verificationCode) {
}
