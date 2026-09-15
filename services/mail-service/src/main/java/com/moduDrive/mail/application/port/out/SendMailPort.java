package com.moduDrive.mail.application.port.out;

import java.util.Map;

public interface SendMailPort {
    void send(String to, String subject, String body);

    /** {@code htmlBody} is trusted, pre-escaped HTML — callers must escape any user-supplied text
     * before interpolating it in. {@code fromDisplayName} overrides the mailbox's own display
     * name for just this message (e.g. "ModuDrive에서 공유") — null keeps the default.
     * {@code inlineSvgImages} (contentId -> SVG markup) are attached as MIME inline parts and must
     * be referenced from {@code htmlBody} as {@code <img src="cid:contentId">} — raw {@code <svg>}
     * markup embedded directly in the body gets stripped by most webmail sanitizers (Naver's
     * included) as a live-DOM XSS vector; an {@code <img>} pointing at an attached resource doesn't
     * trigger that. */
    void sendHtml(String to, String subject, String htmlBody, String fromDisplayName, Map<String, String> inlineSvgImages);
}
