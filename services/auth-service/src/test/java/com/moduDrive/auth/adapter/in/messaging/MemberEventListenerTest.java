package com.moduDrive.auth.adapter.in.messaging;

import com.moduDrive.auth.application.port.in.command.RevokeMemberAccessCommand;
import com.moduDrive.auth.application.port.in.usecase.RevokeMemberAccessUseCase;
import com.moduDrive.common.event.member.MemberPasswordChanged;
import com.moduDrive.common.event.member.MemberQueues;
import com.moduDrive.common.infrastructure.messaging.idempotency.ProcessedEvents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MemberEventListenerTest {

    @Mock private RevokeMemberAccessUseCase revokeMemberAccessUseCase;
    @Mock private ProcessedEvents processedEvents;
    @InjectMocks private MemberEventListener listener;

    private final UUID memberId = UUID.randomUUID();
    private final MemberPasswordChanged event = new MemberPasswordChanged(memberId);

    @Nested
    @DisplayName("처음 받은 메시지면")
    class WhenMessageIsNew {

        @Test
        void revokesTheMembersAccessAndRecordsTheMessage() {
            given(processedEvents.claim(MemberQueues.PASSWORD_CHANGED, "outbox-1")).willReturn(true);

            listener.onPasswordChanged(event, "outbox-1");

            then(revokeMemberAccessUseCase).should().revokeMemberAccess(new RevokeMemberAccessCommand(memberId.toString()));
            then(processedEvents).should().markProcessed(MemberQueues.PASSWORD_CHANGED, "outbox-1");
        }
    }

    @Nested
    @DisplayName("이미 처리한 메시지가 다시 오면")
    class WhenMessageWasAlreadyHandled {

        @Test
        @DisplayName("새 비밀번호로 만든 세션을 지우지 않게 건너뛴다")
        void skipsIt() {
            given(processedEvents.claim(MemberQueues.PASSWORD_CHANGED, "outbox-1")).willReturn(false);

            listener.onPasswordChanged(event, "outbox-1");

            then(revokeMemberAccessUseCase).shouldHaveNoInteractions();
            then(processedEvents).should(never()).markProcessed(anyString(), anyString());
        }
    }
}
