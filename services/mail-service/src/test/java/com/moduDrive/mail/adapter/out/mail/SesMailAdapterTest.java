package com.moduDrive.mail.adapter.out.mail;

import com.moduDrive.mail.application.port.out.MailOutcomeUnknownException;
import com.moduDrive.mail.application.port.out.MailRejectedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.MessageRejectedException;
import software.amazon.awssdk.services.ses.model.MessageTag;
import software.amazon.awssdk.services.ses.model.SendRawEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class SesMailAdapterTest {

    @Mock
    private SesClient sesClient;

    private SesMailAdapter adapter() {
        return new SesMailAdapter(sesClient, "noreply@modudrive.com", "mail-events");
    }

    @SuppressWarnings("unchecked")
    private SendRawEmailRequest sentRequest() {
        ArgumentCaptor<Consumer<SendRawEmailRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        then(sesClient).should().sendRawEmail(captor.capture());
        SendRawEmailRequest.Builder builder = SendRawEmailRequest.builder();
        captor.getValue().accept(builder);
        return builder.build();
    }

    private String rawMime() {
        return sentRequest().rawMessage().data().asUtf8String();
    }

    @Nested
    @DisplayName("HTML 메일을 발송할 때")
    class WhenSendingHtml {

        @Test
        @DisplayName("HTML MIME 메시지를 만들어 SES로 보낸다")
        void sendsAnHtmlMimeMessage() {
            adapter().sendHtml("q_outbox-1", "river@modudrive.com", "subject", "<p>body</p>", null, Map.of());

            assertThat(rawMime()).contains("Subject: subject").contains("text/html").contains("<p>body</p>");
        }

        @Test
        @DisplayName("배달 id를 태그로, 설정 세트와 함께 실어 Send 이벤트에 되돌아오게 한다")
        void tagsTheDeliveryIdUnderTheConfigurationSet() {
            adapter().sendHtml("q_outbox-1", "river@modudrive.com", "subject", "<p>body</p>", null, Map.of());

            SendRawEmailRequest request = sentRequest();
            assertThat(request.configurationSetName()).isEqualTo("mail-events");
            assertThat(request.tags()).containsExactly(
                    MessageTag.builder().name(SesMailAdapter.DELIVERY_TAG).value("q_outbox-1").build());
        }

        @Test
        @DisplayName("표시 이름이 주어지면 발신자 표시 이름을 바꾼다")
        void overridesTheFromDisplayNameWhenGiven() {
            adapter().sendHtml("q_outbox-1", "river@modudrive.com", "subject", "<p>body</p>", "ModuDrive", Map.of());

            assertThat(rawMime()).contains("From: ModuDrive <noreply@modudrive.com>");
        }

        @Test
        @DisplayName("인라인 이미지를 Content-ID로 첨부한다")
        void attachesInlineImagesReferencedByContentId() {
            adapter().sendHtml("q_outbox-1", "river@modudrive.com", "subject", "<img src=\"cid:logo\">", null,
                    Map.of("logo", new byte[] {(byte) 0x89, 'P', 'N', 'G'}));

            assertThat(rawMime()).contains("Content-ID: <logo>").contains("image/png");
        }
    }

    @Nested
    @DisplayName("발송이 실패하면")
    class WhenSendingFails {

        @Test
        @DisplayName("응답을 기다리다 시간 제한에 걸리면 결과를 알 수 없다고 알린다")
        @SuppressWarnings("unchecked")
        void reportsATimeoutAsUnknownOutcome() {
            willThrow(ApiCallTimeoutException.create(7000)).given(sesClient).sendRawEmail(any(Consumer.class));

            assertThatThrownBy(() -> adapter().sendHtml("q_outbox-1", "river@modudrive.com", "s", "<p>b</p>", null, Map.of()))
                    .isInstanceOf(MailOutcomeUnknownException.class);
        }

        @Test
        @DisplayName("SES가 이 요청 자체를 거절하면(400) 다시 보내도 같으니 거절로 알린다")
        @SuppressWarnings("unchecked")
        void reportsARefusalAsRejected() {
            willThrow(MessageRejectedException.builder().statusCode(400)
                    .awsErrorDetails(AwsErrorDetails.builder().errorCode("MessageRejected").build()).build())
                    .given(sesClient).sendRawEmail(any(Consumer.class));

            assertThatThrownBy(() -> adapter().sendHtml("q_outbox-1", "river@modudrive.com", "s", "<p>b</p>", null, Map.of()))
                    .isInstanceOf(MailRejectedException.class);
        }

        @Test
        @DisplayName("400이어도 발송 한도 초과(Throttling)는 잠깐 뒤면 되니 그대로 던져 재시도하게 한다")
        @SuppressWarnings("unchecked")
        void rethrowsThrottling() {
            willThrow(SesException.builder().statusCode(400)
                    .awsErrorDetails(AwsErrorDetails.builder().errorCode("Throttling").build()).build())
                    .given(sesClient).sendRawEmail(any(Consumer.class));

            assertThatThrownBy(() -> adapter().sendHtml("q_outbox-1", "river@modudrive.com", "s", "<p>b</p>", null, Map.of()))
                    .isInstanceOf(SesException.class)
                    .isNotInstanceOf(MailRejectedException.class);
        }

        @Test
        @DisplayName("SES가 서버 에러로 답하면 그대로 던져 재시도하게 한다")
        @SuppressWarnings("unchecked")
        void rethrowsAnErrorAnswer() {
            willThrow(SesException.builder().statusCode(503).build()).given(sesClient).sendRawEmail(any(Consumer.class));

            assertThatThrownBy(() -> adapter().sendHtml("q_outbox-1", "river@modudrive.com", "s", "<p>b</p>", null, Map.of()))
                    .isInstanceOf(SesException.class);
        }
    }
}
