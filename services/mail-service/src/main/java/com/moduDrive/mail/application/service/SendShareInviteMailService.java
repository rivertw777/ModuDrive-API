package com.moduDrive.mail.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.mail.application.port.in.command.SendShareInviteMailCommand;
import com.moduDrive.mail.application.port.in.usecase.SendShareInviteMailUseCase;
import com.moduDrive.mail.application.port.out.SendMailPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;

@UseCase
class SendShareInviteMailService implements SendShareInviteMailUseCase {

    private final SendMailPort sendMailPort;
    private final String clientUrl;
    /** Only the guest mail carries the no-login link and the forwarding warning. */
    private final String guestTemplate = MailTemplates.load("/templates/share-invite-guest-mail.html");
    private final String memberTemplate = MailTemplates.load("/templates/share-invite-member-mail.html");

    SendShareInviteMailService(SendMailPort sendMailPort, @Value("${client.url}") String clientUrl) {
        this.sendMailPort = sendMailPort;
        this.clientUrl = clientUrl;
    }

    @Override
    public void sendShareInviteMail(SendShareInviteMailCommand command) {
        // Granter name/email are null when file-service couldn't resolve the member
        // (FindMemberByIdPort#findMemberByIdOrUnknown); the invite must still go out.
        String rawName = sanitizeGranterName(command.getGranterName());
        String rawEmail = command.getGranterEmail() == null ? "" : command.getGranterEmail();
        String granterName = HtmlUtils.htmlEscape(rawName);
        String granterEmail = HtmlUtils.htmlEscape(rawEmail);
        String fileName = HtmlUtils.htmlEscape(command.getFileName());
        String granterInitial = HtmlUtils.htmlEscape(new String(Character.toChars(rawName.codePointAt(0))));
        // The subject is a mail header: htmlEscape doesn't stop CR/LF header injection.
        String subject = "[ModuDrive] \"%s\" 항목이 나와 공유되었습니다".formatted(headerSafe(command.getFileName()));

        // /files/{fileId} is the one file address for every visitor; a signed-out member goes through
        // login and back. A guest invite adds key, which opens it without logging in. Mails outlive
        // redirect aliases, so don't emit the /public/{fileId} alias.
        boolean isGuestInvite = command.getInviteToken() != null;
        String fileLink = "%s/files/%s".formatted(clientUrl, command.getFileId());
        String link = isGuestInvite ? fileLink + "?key=" + command.getInviteToken() : fileLink;

        String messageBlock = command.getMessage() == null || command.getMessage().isBlank() ? "" : """
                <p style="margin:0 0 24px;padding:14px 18px;background:#f8fafc;border-left:3px solid #336fa3;border-radius:4px;font-size:15px;line-height:1.6;color:#334155;white-space:pre-wrap;">%s</p>
                """.formatted(HtmlUtils.htmlEscape(command.getMessage()));

        String html = (isGuestInvite ? guestTemplate : memberTemplate)
                .replace("{{GRANTER_INITIAL}}", granterInitial)
                .replace("{{GRANTER_NAME}}", granterName)
                .replace("{{GRANTER_EMAIL}}", granterEmail)
                .replace("{{MESSAGE_BLOCK}}", messageBlock)
                .replace("{{FILE_NAME}}", fileName)
                .replace("{{LINK}}", link);

        // The granter's name, not email: a display name holding an address other than the real From
        // is what phishing filters (Naver's "안전하지 않은 메일" included) flag as spoofing.
        String fromDisplayName = "%s (ModuDrive에서 공유)".formatted(rawName);
        byte[] fileIcon = FileIcon.of(command.getCategory(), command.isDirectory()).png;
        Map<String, byte[]> inlineImages = isGuestInvite
                ? Map.of("logo", MailTemplates.LOGO_PNG, "fileIcon", fileIcon, "warning", MailTemplates.WARNING_PNG)
                : Map.of("logo", MailTemplates.LOGO_PNG, "fileIcon", fileIcon);
        sendMailPort.sendHtml(command.getDeliveryId(), command.getEmail(), subject, html, fromDisplayName, inlineImages);
    }

    /** A signup name is free text and lands in the From header, so a name like
     * "ModuDrive Security <security@modudrive.com>" would look like a spoofed sender. Strips angle
     * brackets, {@code @}, quotes and control/format characters (CR/LF injection, bidi overrides),
     * caps the length, and never returns blank. */
    private static String sanitizeGranterName(String name) {
        if (name == null) {
            return "ModuDrive 사용자";
        }
        String cleaned = name.replaceAll("[\\p{Cntrl}\\p{Cf}<>@\"]", "").strip();
        if (cleaned.isEmpty()) {
            return "ModuDrive 사용자";
        }
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }

    /** Strips CR/LF/NUL so a value can't inject an extra header line. */
    private static String headerSafe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\u0000]", " ");
    }

    /** The web entry-icon.tsx icons as PNGs. Names must match file-service's {@code FileCategory},
     * which decides the category. */
    enum FileIcon {
        IMAGE, VIDEO, AUDIO, DOCUMENT, OTHER, FOLDER;

        final byte[] png = MailTemplates.image("file-" + name().toLowerCase() + ".png");

        static FileIcon of(String category, boolean directory) {
            if (directory) {
                return FOLDER;
            }
            try {
                return valueOf(category);
            } catch (IllegalArgumentException | NullPointerException e) {
                // Unknown or missing category: fall back rather than fail the mail over an icon.
                return OTHER;
            }
        }
    }
}
