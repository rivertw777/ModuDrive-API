package com.moduDrive.mail.application.port.out;

import java.util.Map;

public interface SendMailPort {
    /** {@code htmlBody} is trusted, pre-escaped HTML — callers must escape any user-supplied text
     * before interpolating it in. {@code fromDisplayName} overrides the mailbox's own display
     * name for just this message (e.g. "ModuDrive에서 공유") — null keeps the default.
     * {@code inlinePngImages} (contentId -> PNG bytes) are attached as MIME inline parts and must
     * be referenced from {@code htmlBody} as {@code <img src="cid:contentId">}. PNG, not SVG: raw
     * {@code <svg>} in the body is stripped by webmail sanitizers as an XSS vector, and an attached
     * SVG still shows as a broken image in Naver and Gmail. */
    void sendHtml(String to, String subject, String htmlBody, String fromDisplayName, Map<String, byte[]> inlinePngImages);
}
