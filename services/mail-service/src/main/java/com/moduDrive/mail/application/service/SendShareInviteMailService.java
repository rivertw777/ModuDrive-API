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
    /** A guest (no ModuDrive account) gets the no-login link plus a warning about forwarding it;
     * a member gets neither — so each has its own file. */
    private final String guestTemplate = MailTemplates.load("/templates/share-invite-guest-mail.html");
    private final String memberTemplate = MailTemplates.load("/templates/share-invite-member-mail.html");

    SendShareInviteMailService(SendMailPort sendMailPort, @Value("${client.url}") String clientUrl) {
        this.sendMailPort = sendMailPort;
        this.clientUrl = clientUrl;
    }

    @Override
    public void sendShareInviteMail(SendShareInviteMailCommand command) {
        // command.getGranterName()/getGranterEmail() are null when file-service's
        // FindMemberByIdPort#findMemberByIdOrUnknown degraded to UNKNOWN_MEMBER (member-service
        // hiccup, or the granter's account is gone by the time this mail is processed) — that
        // degradation is meant to let the invite still go out, so it must not crash here.
        // sanitizeGranterName also strips <, >, @, " and control/format characters: a member's
        // signup name is free text with no such restriction, so without this a name like
        // "ModuDrive Security <security@modudrive.com>" would render in the From display name
        // exactly like a spoofed sender.
        String rawName = sanitizeGranterName(command.getGranterName());
        String rawEmail = command.getGranterEmail() == null ? "" : command.getGranterEmail();
        String granterName = HtmlUtils.htmlEscape(rawName);
        String granterEmail = HtmlUtils.htmlEscape(rawEmail);
        String fileName = HtmlUtils.htmlEscape(command.getFileName());
        String granterInitial = HtmlUtils.htmlEscape(new String(Character.toChars(rawName.codePointAt(0))));
        // headerSafe: these two also land in mail headers (Subject / From personal), not just the
        // HTML body — htmlEscape alone wouldn't stop a CR/LF from injecting an extra header line.
        String subject = "[ModuDrive] \"%s\" 항목이 나와 공유되었습니다".formatted(headerSafe(command.getFileName()));

        // /files/{fileId} is the one canonical file address for every visitor (spec 3): a signed-in
        // member lands on the file, a signed-out one goes through login and back to it. A guest
        // invite (no ModuDrive account for this email) also carries key, the capability that
        // opens it without logging in and survives them signing up. Mails outlive redirect
        // aliases, so never emit the legacy /public/{fileId} form here.
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

        // Shows as e.g. "홍길동 (ModuDrive에서 공유)" in the recipient's inbox, even though the
        // mailbox itself is the shared modudrive.mail.from address. Deliberately the granter's
        // *name*, not their email — a display name containing an address that differs from the
        // real From address is exactly the pattern phishing filters (Naver's "안전하지 않은 메일"
        // warning included) flag as spoofing. rawName is already sanitized above, so this can't
        // reintroduce that same pattern via a crafted signup name.
        String fromDisplayName = "%s (ModuDrive에서 공유)".formatted(rawName);
        // Referenced from the template as cid:logo / cid:fileIcon — see SendMailPort.sendHtml's
        // doc comment for why these ride as MIME attachments instead of raw <svg> in the body.
        byte[] fileIcon = FileIcon.of(command.getCategory(), command.isDirectory()).png;
        Map<String, byte[]> inlineImages = isGuestInvite
                ? Map.of("logo", MailTemplates.LOGO_PNG, "fileIcon", fileIcon, "warning", MailTemplates.WARNING_PNG)
                : Map.of("logo", MailTemplates.LOGO_PNG, "fileIcon", fileIcon);
        sendMailPort.sendHtml(command.getEmail(), subject, html, fromDisplayName, inlineImages);
    }

    /** A member's signup name is free text with no character/format restriction beyond
     * {@code @NotBlank} — this is the one place it crosses into a mail header (From personal
     * name), so it's sanitized here rather than trusting the source. Strips angle brackets, {@code
     * @}, quotes, and control/format characters (this also closes CR/LF header-injection and
     * Unicode bidi-override tricks), caps length, and never returns blank. */
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

    /** Strips CR/LF/NUL so a value can't inject an extra header line when it lands in Subject or
     * the From personal name — htmlEscape alone only protects the HTML body, not mail headers. */
    private static String headerSafe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\u0000]", " ");
    }

    /** Same per-type icons/colors as the frontend's entry-icon.tsx, pre-rendered to PNG. Which
     * category a file falls into is NOT decided here: file-service (the one source of truth, via
     * FileCategory.of) computes it and hands it over as {@code command.getCategory()}, so these
     * names must keep matching file-service's FileCategory names exactly. */
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
                // Unknown/missing category (e.g. file-service added one this enum doesn't know
                // yet) — fall back rather than break the whole mail over an icon.
                return OTHER;
            }
        }
    }
}
