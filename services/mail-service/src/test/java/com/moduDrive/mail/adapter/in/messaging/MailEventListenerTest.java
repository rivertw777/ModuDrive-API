package com.moduDrive.mail.adapter.in.messaging;

import com.moduDrive.common.event.auth.AuthQueues;
import com.moduDrive.common.event.auth.LoginVerificationMailRequested;
import com.moduDrive.common.event.member.MemberQueues;
import com.moduDrive.common.event.member.SignUpVerificationMailRequested;
import com.moduDrive.common.infrastructure.messaging.PermanentConsumeException;
import com.moduDrive.common.infrastructure.messaging.RetryLaterException;
import com.moduDrive.common.infrastructure.messaging.idempotency.ProcessedEvents;
import com.moduDrive.mail.application.port.in.command.SendVerificationMailCommand.Purpose;
import com.moduDrive.mail.application.port.in.command.SendVerificationMailCommand;
import com.moduDrive.mail.application.port.in.usecase.SendShareInviteMailUseCase;
import com.moduDrive.mail.application.port.in.usecase.SendVerificationMailUseCase;
import com.moduDrive.mail.application.port.out.MailOutcomeUnknownException;
import com.moduDrive.mail.application.port.out.MailRejectedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class MailEventListenerTest {

    @Mock private SendVerificationMailUseCase sendVerificationMailUseCase;
    @Mock private SendShareInviteMailUseCase sendShareInviteMailUseCase;
    @Mock private ProcessedEvents processedEvents;
    @Spy private JsonMapper jsonMapper = new JsonMapper();
    @InjectMocks private MailEventListener listener;

    private final SignUpVerificationMailRequested event = new SignUpVerificationMailRequested("river@modudrive.com", "042917");

    @Nested
    @DisplayName("처음 받은 메시지면")
    class WhenMessageIsNew {

        @Test
        @DisplayName("메일을 보낸 뒤에 처리 기록을 남긴다")
        void sendsTheMailThenRecordsIt() {
            given(processedEvents.claim(MemberQueues.SIGN_UP_VERIFICATION_MAIL_REQUESTED, "outbox-1")).willReturn(true);

            listener.onSignUpVerificationRequested(event, "outbox-1");

            InOrder inOrder = inOrder(sendVerificationMailUseCase, processedEvents);
            inOrder.verify(sendVerificationMailUseCase).sendVerificationMail(any(SendVerificationMailCommand.class));
            inOrder.verify(processedEvents).markProcessed(MemberQueues.SIGN_UP_VERIFICATION_MAIL_REQUESTED, "outbox-1");
        }

        @Test
        @DisplayName("발송이 실패하면 선점을 돌려놓고 기록하지 않아 재시도 때 다시 보낸다")
        void releasesTheClaimWhenSendingFails() {
            given(processedEvents.claim(MemberQueues.SIGN_UP_VERIFICATION_MAIL_REQUESTED, "outbox-1")).willReturn(true);
            willThrow(new IllegalStateException("smtp down")).given(sendVerificationMailUseCase)
                    .sendVerificationMail(any(SendVerificationMailCommand.class));

            try {
                listener.onSignUpVerificationRequested(event, "outbox-1");
            } catch (IllegalStateException expected) {
                // the listener lets it out so the message is retried
            }

            then(processedEvents).should().release(MemberQueues.SIGN_UP_VERIFICATION_MAIL_REQUESTED, "outbox-1");
            then(processedEvents).should(never()).markProcessed(anyString(), anyString());
        }

        @Test
        @DisplayName("발송 결과를 알 수 없으면 선점을 돌려놓지 않고, Send 이벤트를 기다린 뒤에 재시도하게 한다")
        void waitsForTheSendEventWhenTheOutcomeIsUnknown() {
            given(processedEvents.claim(MemberQueues.SIGN_UP_VERIFICATION_MAIL_REQUESTED, "outbox-1")).willReturn(true);
            willThrow(new MailOutcomeUnknownException(new RuntimeException("timeout"))).given(sendVerificationMailUseCase)
                    .sendVerificationMail(any(SendVerificationMailCommand.class));

            assertThatThrownBy(() -> listener.onSignUpVerificationRequested(event, "outbox-1"))
                    .isInstanceOfSatisfying(RetryLaterException.class,
                            e -> assertThat(e.delay()).isEqualTo(MailEventListener.SEND_EVENT_WAIT));
            then(processedEvents).should(never()).release(anyString(), anyString());
            then(processedEvents).should(never()).markProcessed(anyString(), anyString());
        }

        @Test
        @DisplayName("SES가 거절한 메일이면 재시도 없이 DLQ로 보내고, 수정 후 재처리할 수 있게 선점을 돌려놓는다")
        void sendsARejectedMailStraightToTheDlq() {
            given(processedEvents.claim(MemberQueues.SIGN_UP_VERIFICATION_MAIL_REQUESTED, "outbox-1")).willReturn(true);
            willThrow(new MailRejectedException(new RuntimeException("Email address is not verified."))).given(sendVerificationMailUseCase)
                    .sendVerificationMail(any(SendVerificationMailCommand.class));

            assertThatThrownBy(() -> listener.onSignUpVerificationRequested(event, "outbox-1"))
                    .isInstanceOf(PermanentConsumeException.class);
            then(processedEvents).should().release(MemberQueues.SIGN_UP_VERIFICATION_MAIL_REQUESTED, "outbox-1");
        }

        @Test
        @DisplayName("큐와 중복 제거 id로 배달 id를 만들어 넘긴다")
        void passesTheDeliveryId() {
            given(processedEvents.claim(MemberQueues.SIGN_UP_VERIFICATION_MAIL_REQUESTED, "outbox-1")).willReturn(true);

            listener.onSignUpVerificationRequested(event, "outbox-1");

            ArgumentCaptor<SendVerificationMailCommand> captor = ArgumentCaptor.forClass(SendVerificationMailCommand.class);
            then(sendVerificationMailUseCase).should().sendVerificationMail(captor.capture());
            assertThat(captor.getValue().getDeliveryId())
                    .isEqualTo(MemberQueues.SIGN_UP_VERIFICATION_MAIL_REQUESTED + "_outbox-1");
        }
    }

    @Nested
    @DisplayName("SES 이벤트를 받으면")
    class WhenSesEventArrives {

        private String sesEvent(String type, String deliveryId) {
            return """
                    {"eventType": "%s", "mail": {"messageId": "m-1", "tags": {"deliveryId": ["%s"], "ses:caller-identity": ["x"]}}, "send": {}}
                    """.formatted(type, deliveryId);
        }

        @Test
        @DisplayName("Send 이벤트면 그 메시지를 처리 완료로 기록해, 재수신 때 다시 보내지 않게 한다")
        void recordsTheTaggedMessageAsProcessed() {
            listener.onSesEvent(sesEvent("Send", "mail-verification-requested_outbox-12"));

            then(processedEvents).should().markProcessed("mail-verification-requested", "outbox-12");
        }

        @Test
        @DisplayName("구분자가 없는 배달 id는 무시한다")
        void ignoresMalformedDeliveryIds() {
            listener.onSesEvent(sesEvent("Send", "mail-verification-requested"));

            then(processedEvents).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("Send가 아닌 이벤트는 무시한다")
        void ignoresOtherEventTypes() {
            listener.onSesEvent(sesEvent("Delivery", "mail-verification-requested_outbox-12"));

            then(processedEvents).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("구독 확인 문구처럼 JSON이 아닌 메시지는 무시한다")
        void ignoresNonJsonBodies() {
            listener.onSesEvent("Successfully validated SNS topic for Amazon SES event publishing.");

            then(processedEvents).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("배달 id 태그가 없으면 무시한다")
        void ignoresUntaggedEvents() {
            listener.onSesEvent("""
                    {"eventType": "Send", "mail": {"tags": {}}}
                    """);

            then(processedEvents).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("이미 처리한 메시지가 다시 오면")
    class WhenMessageWasAlreadyHandled {

        @Test
        void skipsItWithoutSendingAgain() {
            given(processedEvents.claim(MemberQueues.SIGN_UP_VERIFICATION_MAIL_REQUESTED, "outbox-1")).willReturn(false);

            listener.onSignUpVerificationRequested(event, "outbox-1");

            then(sendVerificationMailUseCase).shouldHaveNoInteractions();
            then(processedEvents).should(never()).markProcessed(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("메일 종류에 따라")
    class WhenMappingPurpose {

        private SendVerificationMailCommand sentCommand() {
            ArgumentCaptor<SendVerificationMailCommand> captor = ArgumentCaptor.forClass(SendVerificationMailCommand.class);
            then(sendVerificationMailUseCase).should().sendVerificationMail(captor.capture());
            return captor.getValue();
        }

        @Test
        @DisplayName("회원가입 인증 큐면 회원가입 인증 메일을 보낸다")
        void sendsSignUpMailForSignUpQueue() {
            given(processedEvents.claim(MemberQueues.SIGN_UP_VERIFICATION_MAIL_REQUESTED, "outbox-1")).willReturn(true);

            listener.onSignUpVerificationRequested(event, "outbox-1");

            assertThat(sentCommand().getPurpose()).isEqualTo(Purpose.SIGN_UP);
        }

        @Test
        @DisplayName("새 기기 로그인 큐면 로그인 인증 메일을 보내고, 그 큐로 처리 기록을 남긴다")
        void sendsLoginMailForLoginQueue() {
            given(processedEvents.claim(AuthQueues.LOGIN_VERIFICATION_MAIL_REQUESTED, "outbox-1")).willReturn(true);

            listener.onLoginVerificationRequested(new LoginVerificationMailRequested("river@modudrive.com", "042917"), "outbox-1");

            assertThat(sentCommand().getPurpose()).isEqualTo(Purpose.LOGIN);
            then(processedEvents).should().markProcessed(AuthQueues.LOGIN_VERIFICATION_MAIL_REQUESTED, "outbox-1");
        }
    }
}
