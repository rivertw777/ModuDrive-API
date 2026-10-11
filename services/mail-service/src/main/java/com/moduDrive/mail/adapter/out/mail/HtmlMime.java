package com.moduDrive.mail.adapter.out.mail;

import com.moduDrive.mail.application.port.out.MailRejectedException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.MimeMessageHelper;

/** The MIME message both transports send — see {@link com.moduDrive.mail.application.port.out.SendMailPort}. */
final class HtmlMime {

    private HtmlMime() {
    }

    static MimeMessage build(MimeMessage message, String from, String to, String subject, String htmlBody,
            String fromDisplayName, Map<String, byte[]> inlinePngImages) {
        try {
            // multipart=true (multipart/related) is required for addInline below — without it
            // the helper has nowhere to attach the inline resources.
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            if (fromDisplayName != null) {
                helper.setFrom(from, fromDisplayName);
            } else {
                helper.setFrom(from);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            for (Map.Entry<String, byte[]> image : inlinePngImages.entrySet()) {
                helper.addInline(image.getKey(), new ByteArrayResource(image.getValue()), "image/png");
            }
            return message;
        } catch (MessagingException | UnsupportedEncodingException e) {
            // Malformed MIME structure, not a sending failure — retrying the same body would fail identically.
            // Type only: an AddressException's message quotes the address.
            throw new MailRejectedException(e.getClass().getName());
        }
    }
}
