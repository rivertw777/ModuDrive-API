package com.moduDrive.member.application.port.in.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ConfirmEmailVerificationCommand {

    private final String token;

    public ConfirmEmailVerificationCommand(String token) {
        this.token = token;
    }
}
