package com.moduDrive.mail.adapter.out.mail;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class JavaMailSenderAdapterTest {

    @Mock
    private JavaMailSender javaMailSender;

    private JavaMailSenderAdapter adapter() {
        return new JavaMailSenderAdapter(javaMailSender, "noreply@modudrive.com");
    }

    @Nested
    @DisplayName("메일을 발송할 때")
    class WhenSending {

        @Test
        void buildsMessageFromFromAddressAndDelegatesToJavaMailSender() {
            adapter().send("river@modudrive.com", "subject", "body");

            ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
            then(javaMailSender).should().send(captor.capture());
            SimpleMailMessage sent = captor.getValue();
            assertThat(sent.getFrom()).isEqualTo("noreply@modudrive.com");
            assertThat(sent.getTo()).containsExactly("river@modudrive.com");
            assertThat(sent.getSubject()).isEqualTo("subject");
            assertThat(sent.getText()).isEqualTo("body");
        }
    }

    @Nested
    @DisplayName("HTML 메일을 발송할 때")
    class WhenSendingHtml {

        @Test
        void buildsAnHtmlMimeMessageAndDelegatesToJavaMailSender() throws Exception {
            MimeMessage mimeMessage = new MimeMessage(jakarta.mail.Session.getDefaultInstance(new java.util.Properties()));
            given(javaMailSender.createMimeMessage()).willReturn(mimeMessage);

            adapter().sendHtml("river@modudrive.com", "subject", "<p>body</p>", null, Map.of());

            then(javaMailSender).should().send(mimeMessage);
            assertThat(mimeMessage.getSubject()).isEqualTo("subject");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            mimeMessage.writeTo(out); // writeTo() calls saveChanges() first, which commits the Content-Type header
            assertThat(out.toString()).contains("text/html").contains("<p>body</p>");
        }

        @Test
        void overridesTheFromDisplayNameWhenGiven() throws Exception {
            MimeMessage mimeMessage = new MimeMessage(jakarta.mail.Session.getDefaultInstance(new java.util.Properties()));
            given(javaMailSender.createMimeMessage()).willReturn(mimeMessage);

            adapter().sendHtml("river@modudrive.com", "subject", "<p>body</p>", "ModuDrive에서 공유", Map.of());

            assertThat(mimeMessage.getFrom()).hasSize(1);
            assertThat(((jakarta.mail.internet.InternetAddress) mimeMessage.getFrom()[0]).getPersonal())
                    .isEqualTo("ModuDrive에서 공유");
            assertThat(((jakarta.mail.internet.InternetAddress) mimeMessage.getFrom()[0]).getAddress())
                    .isEqualTo("noreply@modudrive.com");
        }

        @Test
        void attachesInlineImagesReferencedByContentId() throws Exception {
            MimeMessage mimeMessage = new MimeMessage(jakarta.mail.Session.getDefaultInstance(new java.util.Properties()));
            given(javaMailSender.createMimeMessage()).willReturn(mimeMessage);

            adapter().sendHtml("river@modudrive.com", "subject", "<img src=\"cid:logo\">", null,
                    Map.of("logo", "<svg>icon</svg>"));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            mimeMessage.writeTo(out);
            String raw = out.toString();
            assertThat(raw).contains("Content-ID: <logo>").contains("image/svg+xml");
        }
    }
}
