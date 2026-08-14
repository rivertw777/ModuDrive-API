package com.moduDrive.notification.adapter.out.mail;

import com.moduDrive.notification.application.port.out.SendMailPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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
}
