package com.moduDrive.mail.adapter.in.messaging;

import com.moduDrive.common.event.mail.MailQueues;
import com.moduDrive.common.event.mail.VerificationMailRequested;
import com.moduDrive.common.infrastructure.sqs.ProcessedEvents;
import com.moduDrive.mail.application.port.in.command.SendVerificationMailCommand;
import com.moduDrive.mail.application.port.in.usecase.SendShareInviteMailUseCase;
import com.moduDrive.mail.application.port.in.usecase.SendVerificationMailUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private final VerificationMailRequested event = new VerificationMailRequested("river@modudrive.com", "042917");

    @Nested
    @DisplayName("처음 받은 메시지면")
    class WhenMessageIsNew {

        @Test
        @DisplayName("메일을 보낸 뒤에 처리 기록을 남긴다")
        void sendsTheMailThenRecordsIt() {
            given(processedEvents.isProcessed(MailQueues.VERIFICATION_REQUESTED, "outbox-1")).willReturn(false);

            listener.onVerificationRequested(event, "outbox-1");

            InOrder inOrder = inOrder(sendVerificationMailUseCase, processedEvents);
            inOrder.verify(sendVerificationMailUseCase).sendVerificationMail(any(SendVerificationMailCommand.class));
            inOrder.verify(processedEvents).markProcessed(MailQueues.VERIFICATION_REQUESTED, "outbox-1");
        }

        @Test
        @DisplayName("발송이 실패하면 기록하지 않아 재시도 때 다시 보낸다")
        void doesNotRecordWhenSendingFails() {
            given(processedEvents.isProcessed(MailQueues.VERIFICATION_REQUESTED, "outbox-1")).willReturn(false);
            willThrow(new IllegalStateException("smtp down")).given(sendVerificationMailUseCase)
                    .sendVerificationMail(any(SendVerificationMailCommand.class));

            try {
                listener.onVerificationRequested(event, "outbox-1");
            } catch (IllegalStateException expected) {
                // the listener lets it out so the message is retried
            }

            then(processedEvents).should(never()).markProcessed(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("이미 처리한 메시지가 다시 오면")
    class WhenMessageWasAlreadyHandled {

        @Test
        void skipsItWithoutSendingAgain() {
            given(processedEvents.isProcessed(MailQueues.VERIFICATION_REQUESTED, "outbox-1")).willReturn(true);

            listener.onVerificationRequested(event, "outbox-1");

            then(sendVerificationMailUseCase).shouldHaveNoInteractions();
            then(processedEvents).should(never()).markProcessed(anyString(), anyString());
        }
    }
}
