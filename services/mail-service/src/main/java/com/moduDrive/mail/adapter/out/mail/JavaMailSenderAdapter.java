package com.moduDrive.mail.adapter.out.mail;

import com.moduDrive.mail.application.port.out.SendMailPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailParseException;
import org.springframework.mail.SimpleMailMessage;
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
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        // Throws MailException on SMTP failure — left uncaught so the Kafka listener's
        // DefaultErrorHandler (see config.KafkaRetryConfig) retries, then routes to the DLT.
        javaMailSender.send(message);
    }

    @Override
    public void sendHtml(String to, String subject, String htmlBody, String fromDisplayName,
            Map<String, String> inlineSvgImages) {
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
            for (Map.Entry<String, String> image : inlineSvgImages.entrySet()) {
                helper.addInline(image.getKey(),
                        new ByteArrayResource(image.getValue().getBytes(StandardCharsets.UTF_8)),
                        "image/svg+xml");
            }
        } catch (MessagingException | UnsupportedEncodingException e) {
            // Malformed MIME structure, not an SMTP failure — retrying the same body would fail
            // identically, so this doesn't go through the same uncaught-retry path as send().
            throw new MailParseException(e);
        }

        javaMailSender.send(message);
    }
}
