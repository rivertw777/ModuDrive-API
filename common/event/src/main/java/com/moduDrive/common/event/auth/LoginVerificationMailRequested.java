package com.moduDrive.common.event.auth;

/** Published by auth-service (queue {@link AuthQueues#LOGIN_VERIFICATION_MAIL_REQUESTED}) when a login
 * from a device the member hasn't verified needs its emailed code (auth spec 004 2-1). */
public record LoginVerificationMailRequested(String email, String verificationCode) {
}
