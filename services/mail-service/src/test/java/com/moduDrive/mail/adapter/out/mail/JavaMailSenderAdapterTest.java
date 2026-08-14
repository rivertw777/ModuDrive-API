package com.moduDrive.mail.adapter.out.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
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
}
