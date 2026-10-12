package com.moduDrive.mail.adapter.out.mail;

import com.moduDrive.mail.application.port.out.MailRejectedException;
import com.moduDrive.mail.application.port.out.SendMailPort;
import java.util.Map;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/** Plain SMTP (STARTTLS, e.g. Gmail with an app password) for an environment whose SES is still in the
 * sandbox. There is no Send event here, so a send that times out after the server took the mail is
 * retried and can arrive twice. */
@Component
@ConditionalOnProperty(name = "modudrive.mail.transport", havingValue = "smtp")
class SmtpMailAdapter implements SendMailPort {

    private final JavaMailSenderImpl sender = new JavaMailSenderImpl();
    private final String from;

    SmtpMailAdapter(@Value("${modudrive.mail.smtp.host}") String host, @Value("${modudrive.mail.smtp.port}") int port,
                    @Value("${modudrive.mail.smtp.username}") String username,
                    @Value("${modudrive.mail.smtp.password}") String password,
                    @Value("${modudrive.mail.from}") String from) {
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        sender.setDefaultEncoding("UTF-8");
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        // Inside the mail queues' 10s visibility timeout, like SesConfig's cap.
        props.put("mail.smtp.connectiontimeout", "3000");
        props.put("mail.smtp.timeout", "3000");
        props.put("mail.smtp.writetimeout", "3000");
        this.from = from;
    }

    @Override
    public void sendHtml(String deliveryId, String to, String subject, String htmlBody, String fromDisplayName,
            Map<String, byte[]> inlinePngImages) {
        try {
            sender.send(HtmlMime.build(sender.createMimeMessage(), from, to, subject, htmlBody, fromDisplayName,
                    inlinePngImages));
        } catch (MailAuthenticationException e) {
            // Wrong account or app password: every retry fails the same way until the secret is fixed.
            throw new MailRejectedException(e.getClass().getSimpleName());
        }
    }
}
