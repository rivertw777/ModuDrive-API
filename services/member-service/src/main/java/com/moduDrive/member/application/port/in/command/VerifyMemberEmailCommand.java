package com.moduDrive.member.application.port.in.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class VerifyMemberEmailCommand {

    private final String token;

    public VerifyMemberEmailCommand(String token) {
        this.token = token;
    }
}
