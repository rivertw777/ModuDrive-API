package com.moduDrive.mail.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.mail.application.port.in.command.SendShareInviteMailCommand;
import com.moduDrive.mail.application.port.in.usecase.SendShareInviteMailUseCase;
import com.moduDrive.mail.application.port.out.SendMailPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@UseCase
class SendShareInviteMailService implements SendShareInviteMailUseCase {

    private static final String TEMPLATE_PATH = "/templates/share-invite-mail.html";

    private final SendMailPort sendMailPort;
    private final String clientUrl;
    /** Loaded once at construction — the file never changes at runtime, so re-reading it per
     * mail would just be wasted I/O. */
    private final String template;

    SendShareInviteMailService(SendMailPort sendMailPort, @Value("${client.url}") String clientUrl) {
        this.sendMailPort = sendMailPort;
        this.clientUrl = clientUrl;
        this.template = loadTemplate();
    }

    private static String loadTemplate() {
        try (InputStream in = SendShareInviteMailService.class.getResourceAsStream(TEMPLATE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Missing mail template: " + TEMPLATE_PATH);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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

        // A guest invite (no ModuDrive account for this email) has no login-gated deep link to
        // send instead, so the mail carries the no-login link directly. /files/{fileId} is the one
        // canonical file address for every visitor (spec 3); key is the capability that authorizes
        // this guest and survives them signing up. Mails outlive redirect aliases, so never emit
        // the legacy /public/{fileId} form here.
        boolean isGuestInvite = command.getInviteToken() != null;
        String link = isGuestInvite
                ? "%s/files/%s?key=%s".formatted(clientUrl, command.getFileId(), command.getInviteToken())
                : clientUrl;

        String messageBlock = command.getMessage() == null || command.getMessage().isBlank() ? "" : """
                <p style="margin:16px 0;padding:12px 16px;background:#f1f3f4;border-radius:8px;color:#3c4043;white-space:pre-wrap;">%s</p>
                """.formatted(HtmlUtils.htmlEscape(command.getMessage()));

        String guestNotice = !isGuestInvite ? "" : """
                <p style="margin:16px 0;padding:12px 16px;background:#fef7e0;border-radius:8px;color:#5f6368;font-size:13px;">
                  ⚠️ 이 이메일은 로그인하지 않아도 이 항목에 액세스할 수 있는 권한을 부여합니다. 신뢰하는 사용자에게만 전달하세요.
                </p>
                """;

        String html = template
                .replace("{{GRANTER_INITIAL}}", granterInitial)
                .replace("{{GRANTER_NAME}}", granterName)
                .replace("{{GRANTER_EMAIL}}", granterEmail)
                .replace("{{MESSAGE_BLOCK}}", messageBlock)
                .replace("{{FILE_NAME}}", fileName)
                .replace("{{LINK}}", link)
                .replace("{{GUEST_NOTICE}}", guestNotice);

        // Shows as e.g. "홍길동 (ModuDrive에서 공유)" in the recipient's inbox, even though the
        // mailbox itself is the shared modudrive.mail.from address. Deliberately the granter's
        // *name*, not their email — a display name containing an address that differs from the
        // real From address is exactly the pattern phishing filters (Naver's "안전하지 않은 메일"
        // warning included) flag as spoofing. rawName is already sanitized above, so this can't
        // reintroduce that same pattern via a crafted signup name.
        String fromDisplayName = "%s (ModuDrive에서 공유)".formatted(rawName);
        // Referenced from the template as cid:logo / cid:fileIcon — see SendMailPort.sendHtml's
        // doc comment for why these ride as MIME attachments instead of raw <svg> in the body.
        Map<String, String> inlineImages = Map.of(
                "logo", LOGO_SVG,
                "fileIcon", FileIconStyle.svgMarkupFor(command.getCategory(), command.isDirectory()));
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

    private static final String LOGO_SVG = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 36 36">
              <rect width="36" height="36" rx="9" fill="#0f172a" />
              <svg x="8" y="8" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#ffffff" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z" />
              </svg>
            </svg>
            """;

    /** Same per-type icon/color mapping as the frontend's entry-icon.tsx (icons.tsx's stroke paths,
     * copied verbatim) — purely presentational, so mail-service owning it is fine. Which category a
     * file falls into is NOT decided here: file-service (the one source of truth, via FileCategory.of)
     * computes it and hands it over as {@code command.getCategory()}, so this enum's names must keep
     * matching file-service's FileCategory names exactly. */
    private enum FileIconStyle {
        IMAGE("#10b981", """
                <rect x="4" y="4" width="16" height="16" rx="2"/><circle cx="9.5" cy="9.5" r="1.5"/><path d="m5 19 5-5 3 3 4-4 3 3"/>"""),
        VIDEO("#0ea5e9", """
                <rect x="2" y="6" width="14" height="12" rx="2"/><path d="m16 10.5 5.2-3.48a.5.5 0 0 1 .8.4v9.16a.5.5 0 0 1-.8.4L16 13.5"/>"""),
        AUDIO("#f43f5e", """
                <path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/>"""),
        DOCUMENT("#3b82f6", """
                <path d="M18 3h-8l-4 4v13a1 1 0 0 0 1 1h11a1 1 0 0 0 1-1V4a1 1 0 0 0-1-1z"/><path d="M10 3v4h-4"/><path d="M9 10h6M9 13h6M9 16h6M9 19h3"/>"""),
        OTHER("#94a3b8", """
                <path d="M6 3h8l4 4v13a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z"/><path d="M14 3v4h4"/>"""),
        FOLDER("#336fa3", """
                <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>""");

        private final String color;
        private final String paths;

        FileIconStyle(String color, String paths) {
            this.color = color;
            this.paths = paths;
        }

        static String svgMarkupFor(String category, boolean directory) {
            FileIconStyle style = FOLDER;
            if (!directory) {
                try {
                    style = valueOf(category);
                } catch (IllegalArgumentException | NullPointerException e) {
                    // Unknown/missing category (e.g. file-service added one this enum doesn't
                    // know yet) — fall back rather than break the whole mail over an icon.
                    style = OTHER;
                }
            }
            return """
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="%s" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0;">%s</svg>"""
                    .formatted(style.color, style.paths);
        }
    }
}
