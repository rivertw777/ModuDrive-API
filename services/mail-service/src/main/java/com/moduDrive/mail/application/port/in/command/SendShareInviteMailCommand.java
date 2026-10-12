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
    /** file-service's {@code FileCategory} name (IMAGE/VIDEO/AUDIO/DOCUMENT/OTHER), used to pick the icon. */
    private final String category;
    private final UUID fileId;
    private final String granterName;
    private final String granterEmail;
    /** Optional note from the share dialog; null or blank when empty. */
    private final String message;
    /** Non-null only for a guest invite (no member owns the email): sent as {@code /files/{fileId}?key=}
     * so the recipient can open the file without logging in. */
    private final UUID inviteToken;
    /** See {@code SendMailPort.sendHtml}. */
    private final String deliveryId;

    public SendShareInviteMailCommand(
            String email, String fileName, boolean directory, String category, UUID fileId,
            String granterName, String granterEmail, String message, UUID inviteToken,
            String deliveryId) {
        this.email = email;
        this.fileName = fileName;
        this.directory = directory;
        this.category = category;
        this.fileId = fileId;
        this.granterName = granterName;
        this.granterEmail = granterEmail;
        this.message = message;
        this.inviteToken = inviteToken;
        this.deliveryId = deliveryId;
    }
}
