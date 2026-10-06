package com.moduDrive.mail.adapter.out.mail;

import com.moduDrive.mail.application.port.out.MailOutcomeUnknownException;
import com.moduDrive.mail.application.port.out.MailRejectedException;
import com.moduDrive.mail.application.port.out.SendMailPort;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.MessageTag;

/** Sends through the SES API (SendRawEmail) directly rather than Spring Cloud AWS's JavaMailSender,
 * which can't set message tags: every mail carries its delivery id as the {@link #DELIVERY_TAG} tag, and
 * the configuration set publishes SES's Send event with it — the proof that a timed-out send went out. */
@Component
class SesMailAdapter implements SendMailPort {

    static final String DELIVERY_TAG = "deliveryId";

    private final SesClient sesClient;
    private final String from;
    private final String configurationSet;
    private final Session session = Session.getInstance(new Properties());

    SesMailAdapter(SesClient sesClient, @Value("${modudrive.mail.from}") String from,
                   @Value("${modudrive.mail.configuration-set}") String configurationSet) {
        this.sesClient = sesClient;
        this.from = from;
        this.configurationSet = configurationSet;
    }

    @Override
    public void sendHtml(String deliveryId, String to, String subject, String htmlBody, String fromDisplayName,
            Map<String, byte[]> inlinePngImages) {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try {
            MimeMessage message = new MimeMessage(session);
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
            message.writeTo(raw);
        } catch (MessagingException | IOException e) {
            // Malformed MIME structure, not a sending failure — retrying the same body would fail identically.
            throw new MailRejectedException(e);
        }

        // Three outcomes besides success, and only the last is a plain retry:
        //  - a 400 that isn't throttling: SES refused this request (MessageRejected, MailFromDomainNotVerified,
        //    AccountSendingPaused...) and would refuse it again — mirrors SqsFailures on the publish side;
        //  - a timeout: we stopped waiting, SES may not have — the request can still be accepted and sent;
        //  - anything else (5xx, throttling, never reached): nothing went out, so a retry is safe.
        try {
            sesClient.sendRawEmail(r -> r
                    .rawMessage(m -> m.data(SdkBytes.fromByteArray(raw.toByteArray())))
                    .configurationSetName(configurationSet)
                    .tags(MessageTag.builder().name(DELIVERY_TAG).value(deliveryId).build()));
        } catch (AwsServiceException e) {
            if (e.statusCode() == 400 && !e.isThrottlingException()) {
                throw new MailRejectedException(e);
            }
            throw e;
        } catch (ApiCallTimeoutException e) {
            throw new MailOutcomeUnknownException(e);
        }
    }
}
