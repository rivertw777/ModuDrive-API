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
    private final UUID fileId;
    /** Non-null only for a guest invite (no ModuDrive member owns the email) — the mail links
     * straight to the file's no-login public route ({@code /public/{fileId}?key=}) instead of
     * asking the recipient to log in. */
    private final UUID linkToken;

    public SendShareInviteMailCommand(String email, String fileName, String role, UUID fileId, UUID linkToken) {
        this.email = email;
        this.fileName = fileName;
        this.role = role;
        this.fileId = fileId;
        this.linkToken = linkToken;
    }
}
