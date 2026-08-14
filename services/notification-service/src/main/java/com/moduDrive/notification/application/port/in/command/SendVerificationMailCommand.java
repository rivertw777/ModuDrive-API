package com.moduDrive.notification.application.port.in.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class SendVerificationMailCommand {

    private final String email;
    private final String name;
    private final String verificationToken;

    public SendVerificationMailCommand(String email, String name, String verificationToken) {
        this.email = email;
        this.name = name;
        this.verificationToken = verificationToken;
    }
}
