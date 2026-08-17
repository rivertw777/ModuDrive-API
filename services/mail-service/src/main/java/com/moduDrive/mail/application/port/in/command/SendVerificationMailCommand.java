package com.moduDrive.mail.application.port.in.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class SendVerificationMailCommand {

    private final String email;
    private final String verificationCode;

    public SendVerificationMailCommand(String email, String verificationCode) {
        this.email = email;
        this.verificationCode = verificationCode;
    }
}
