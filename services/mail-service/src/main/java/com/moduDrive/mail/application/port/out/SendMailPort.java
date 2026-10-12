package com.moduDrive.mail.application.port.out;

import java.util.Map;

public interface SendMailPort {
    /** {@code htmlBody} is trusted HTML: callers must escape user-supplied text. {@code fromDisplayName}
     * overrides the display name for this message; null keeps the default.
     * {@code inlinePngImages} (contentId -> PNG bytes) are MIME inline parts referenced as
     * {@code <img src="cid:contentId">}. PNG, not SVG: webmail strips inline {@code <svg>}, and an
     * attached SVG shows as a broken image in Naver and Gmail.
     * {@code deliveryId} is the same on every retry of one delivery, so an unknown outcome can be
     * confirmed later (see {@link MailOutcomeUnknownException}). */
    void sendHtml(String deliveryId, String to, String subject, String htmlBody, String fromDisplayName, Map<String, byte[]> inlinePngImages);
}
