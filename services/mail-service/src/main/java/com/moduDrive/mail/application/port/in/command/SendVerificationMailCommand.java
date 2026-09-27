package com.moduDrive.mail.application.port.in.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class SendVerificationMailCommand {

    private final String email;
    private final String verificationCode;
    private final Purpose purpose;

    public SendVerificationMailCommand(String email, String verificationCode, Purpose purpose) {
        this.email = email;
        this.verificationCode = verificationCode;
        this.purpose = purpose;
    }

    /** Signup, or a login from a new device (auth spec 004 2-2) — same code, different wording. */
    public enum Purpose { SIGN_UP, LOGIN }
}
