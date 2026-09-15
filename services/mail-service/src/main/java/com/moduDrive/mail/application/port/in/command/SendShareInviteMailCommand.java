package com.moduDrive.mail.application.port.in.command;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode
public class SendShareInviteMailCommand {

    private final String email;
    private final String fileName;
    private final boolean directory;
    /** file-service's {@code FileCategory} name (IMAGE/VIDEO/AUDIO/DOCUMENT/OTHER) — lets the mail
     * pick a matching file-type icon without mail-service reimplementing the extension mapping. */
    private final String category;
    private final String role;
    private final UUID fileId;
    private final String granterName;
    private final String granterEmail;
    /** Optional note the granter typed into the share dialog. Null/blank when they left it empty. */
    private final String message;
    /** Non-null only for a guest invite (no ModuDrive member owns the email) — this one invite's
     * own capability token, which the mail hands over as {@code /files/{fileId}?key=} so the
     * recipient can open the file without logging in. */
    private final UUID inviteToken;

    public SendShareInviteMailCommand(
            String email, String fileName, boolean directory, String category, String role, UUID fileId,
            String granterName, String granterEmail, String message, UUID inviteToken) {
        this.email = email;
        this.fileName = fileName;
        this.directory = directory;
        this.category = category;
        this.role = role;
        this.fileId = fileId;
        this.granterName = granterName;
        this.granterEmail = granterEmail;
        this.message = message;
        this.inviteToken = inviteToken;
    }
}
