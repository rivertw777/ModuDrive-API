package com.moduDrive.notification.application.port.in.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class SendShareInviteMailCommand {

    private final String email;
    private final String fileName;
    private final String role;

    public SendShareInviteMailCommand(String email, String fileName, String role) {
        this.email = email;
        this.fileName = fileName;
        this.role = role;
    }
}
