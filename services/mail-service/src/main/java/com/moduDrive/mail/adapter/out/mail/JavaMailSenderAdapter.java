package com.moduDrive.mail.adapter.out.mail;

import com.moduDrive.mail.application.port.out.SendMailPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
class JavaMailSenderAdapter implements SendMailPort {

    private final JavaMailSender javaMailSender;
    private final String from;

    JavaMailSenderAdapter(JavaMailSender javaMailSender, @Value("${modudrive.mail.from}") String from) {
        this.javaMailSender = javaMailSender;
        this.from = from;
    }

    @Override
    public void sendHtml(String to, String subject, String htmlBody, String fromDisplayName,
            Map<String, byte[]> inlinePngImages) {
        MimeMessage message = javaMailSender.createMimeMessage();
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
        } catch (MessagingException | UnsupportedEncodingException e) {
            // Malformed MIME structure, not an SMTP failure — retrying the same body would fail
            // identically, so this doesn't go through the same uncaught-retry path as send().
            throw new MailParseException(e);
        }

        // Throws MailException on SMTP failure — left uncaught so the SQS message isn't deleted: it
        // comes back after the visibility timeout and, once the queue's redrive maxReceiveCount is
        // used up, moves to the DLQ.
        javaMailSender.send(message);
    }
}
