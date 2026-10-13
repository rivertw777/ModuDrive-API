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
import org.springframework.stereotype.Component;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.MessageTag;

/** Uses SendRawEmail directly because Spring Cloud AWS's JavaMailSender can't set message tags. Each mail
 * carries its delivery id as the {@link #DELIVERY_TAG} tag, which comes back in SES's Send event. */
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
            HtmlMime.build(new MimeMessage(session), from, to, subject, htmlBody, fromDisplayName, inlinePngImages)
                    .writeTo(raw);
        } catch (IOException | MessagingException e) {
            throw new MailRejectedException(e.getClass().getName());
        }

        // Failure outcomes:
        //  - 400 other than throttling: SES refused the request (MessageRejected, MailFromDomainNotVerified,
        //    AccountSendingPaused...) and would refuse it again;
        //  - timeout: SES may still accept and send it;
        //  - anything else (5xx, throttling, never reached): nothing went out, so a retry is safe.
        try {
            sesClient.sendRawEmail(r -> r
                    .rawMessage(m -> m.data(SdkBytes.fromByteArray(raw.toByteArray())))
                    .configurationSetName(configurationSet)
                    .tags(MessageTag.builder().name(DELIVERY_TAG).value(deliveryId).build()));
        } catch (AwsServiceException e) {
            if (e.statusCode() == 400 && !e.isThrottlingException()) {
                // SES's message can quote the recipient (sandbox: "not verified... failed the check: x@y");
                // the error code and request id say which refusal it was.
                String code = e.awsErrorDetails() == null ? "HTTP 400" : e.awsErrorDetails().errorCode();
                throw new MailRejectedException(
                        e.getClass().getSimpleName() + ": " + code + " (request id " + e.requestId() + ")");
            }
            throw e;
        } catch (ApiCallTimeoutException e) {
            throw new MailOutcomeUnknownException(e);
        }
    }
}
