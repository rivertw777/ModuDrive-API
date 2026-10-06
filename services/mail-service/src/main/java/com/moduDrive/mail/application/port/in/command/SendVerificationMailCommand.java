package com.moduDrive.mail.application.port.in.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class SendVerificationMailCommand {

    private final String email;
    private final String verificationCode;
    private final Purpose purpose;
    /** See {@code SendMailPort.sendHtml}. */
    private final String deliveryId;

    public SendVerificationMailCommand(String email, String verificationCode, Purpose purpose, String deliveryId) {
        this.email = email;
        this.verificationCode = verificationCode;
        this.purpose = purpose;
        this.deliveryId = deliveryId;
    }

    /** Signup, or a login from a new device (auth spec 004 2-1) — same code, different wording. */
    public enum Purpose { SIGN_UP, LOGIN }
}
