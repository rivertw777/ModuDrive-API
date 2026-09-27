package com.moduDrive.mail.adapter.in.messaging;

import com.moduDrive.common.event.mail.MailQueues;
import com.moduDrive.common.event.mail.VerificationMailRequested;
import com.moduDrive.common.infrastructure.messaging.idempotency.ProcessedEvents;
import com.moduDrive.mail.application.port.in.command.SendVerificationMailCommand;
import com.moduDrive.mail.application.port.in.command.SendVerificationMailCommand.Purpose;
import com.moduDrive.mail.application.port.in.usecase.SendShareInviteMailUseCase;
import com.moduDrive.mail.application.port.in.usecase.SendVerificationMailUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
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
    @InjectMocks private MailEventListener listener;

    private final VerificationMailRequested event = new VerificationMailRequested("river@modudrive.com", "042917", VerificationMailRequested.Purpose.SIGN_UP);

    @Nested
    @DisplayName("처음 받은 메시지면")
    class WhenMessageIsNew {

        @Test
        @DisplayName("메일을 보낸 뒤에 처리 기록을 남긴다")
        void sendsTheMailThenRecordsIt() {
            given(processedEvents.claim(MailQueues.VERIFICATION_REQUESTED, "outbox-1")).willReturn(true);

            listener.onVerificationRequested(event, "outbox-1");

            InOrder inOrder = inOrder(sendVerificationMailUseCase, processedEvents);
            inOrder.verify(sendVerificationMailUseCase).sendVerificationMail(any(SendVerificationMailCommand.class));
            inOrder.verify(processedEvents).markProcessed(MailQueues.VERIFICATION_REQUESTED, "outbox-1");
        }

        @Test
        @DisplayName("발송이 실패하면 선점을 돌려놓고 기록하지 않아 재시도 때 다시 보낸다")
        void releasesTheClaimWhenSendingFails() {
            given(processedEvents.claim(MailQueues.VERIFICATION_REQUESTED, "outbox-1")).willReturn(true);
            willThrow(new IllegalStateException("smtp down")).given(sendVerificationMailUseCase)
                    .sendVerificationMail(any(SendVerificationMailCommand.class));

            try {
                listener.onVerificationRequested(event, "outbox-1");
            } catch (IllegalStateException expected) {
                // the listener lets it out so the message is retried
            }

            then(processedEvents).should().release(MailQueues.VERIFICATION_REQUESTED, "outbox-1");
            then(processedEvents).should(never()).markProcessed(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("이미 처리한 메시지가 다시 오면")
    class WhenMessageWasAlreadyHandled {

        @Test
        void skipsItWithoutSendingAgain() {
            given(processedEvents.claim(MailQueues.VERIFICATION_REQUESTED, "outbox-1")).willReturn(false);

            listener.onVerificationRequested(event, "outbox-1");

            then(sendVerificationMailUseCase).shouldHaveNoInteractions();
            then(processedEvents).should(never()).markProcessed(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("메일 목적에 따라")
    class WhenMappingPurpose {

        private SendVerificationMailCommand sentFor(VerificationMailRequested.Purpose purpose) {
            given(processedEvents.claim(MailQueues.VERIFICATION_REQUESTED, "outbox-1")).willReturn(true);

            listener.onVerificationRequested(
                    new VerificationMailRequested("river@modudrive.com", "042917", purpose), "outbox-1");

            ArgumentCaptor<SendVerificationMailCommand> captor = ArgumentCaptor.forClass(SendVerificationMailCommand.class);
            then(sendVerificationMailUseCase).should().sendVerificationMail(captor.capture());
            return captor.getValue();
        }

        @Test
        @DisplayName("새 기기 로그인이면 로그인 인증 메일을 보낸다")
        void sendsLoginMailForLogin() {
            assertThat(sentFor(VerificationMailRequested.Purpose.LOGIN).getPurpose()).isEqualTo(Purpose.LOGIN);
        }

        @Test
        @DisplayName("목적이 없는 이전 메시지는 회원가입 메일로 본다")
        void treatsMissingPurposeAsSignUp() {
            assertThat(sentFor(null).getPurpose()).isEqualTo(Purpose.SIGN_UP);
        }
    }
}
