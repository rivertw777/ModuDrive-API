package com.moduDrive.mail.application.port.in.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode
public class SendShareInviteMailCommand {

    private final String email;
    private final String fileName;
    private final String role;
    /** Non-null only for a guest invite (no ModuDrive member owns the email) — the mail links
     * straight to the file's no-login public route instead of asking the recipient to log in. */
    private final UUID linkToken;

    public SendShareInviteMailCommand(String email, String fileName, String role, UUID linkToken) {
        this.email = email;
        this.fileName = fileName;
        this.role = role;
        this.linkToken = linkToken;
    }
}
