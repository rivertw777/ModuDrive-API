package com.moduDrive.file.adapter.in.messaging;

import com.moduDrive.common.event.member.MemberQueues;
import com.moduDrive.common.event.member.MemberSignedUp;
import com.moduDrive.common.infrastructure.messaging.idempotency.ProcessedEvents;
import com.moduDrive.file.application.port.in.command.ClaimPendingFileSharesCommand;
import com.moduDrive.file.application.port.in.command.CreateNamespaceCommand;
import com.moduDrive.file.application.port.in.usecase.ClaimPendingFileSharesUseCase;
import com.moduDrive.file.application.port.in.usecase.CreateNamespaceUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MemberEventListenerTest {

    @Mock private CreateNamespaceUseCase createNamespaceUseCase;
    @Mock private ClaimPendingFileSharesUseCase claimPendingFileSharesUseCase;
    @Mock private ProcessedEvents processedEvents;
    @InjectMocks private MemberEventListener listener;

    private final UUID memberId = UUID.randomUUID();
    private final MemberSignedUp event = new MemberSignedUp(memberId, "river@modudrive.com");

    @Nested
    @DisplayName("처음 받은 메시지면")
    class WhenMessageIsNew {

        @Test
        void createsTheNamespaceThenClaimsPendingSharesAndRecordsTheMessage() {
            given(processedEvents.claim(MemberQueues.SIGNED_UP, "outbox-1")).willReturn(true);

            listener.onMemberSignedUp(event, "outbox-1");

            InOrder order = inOrder(createNamespaceUseCase, claimPendingFileSharesUseCase);
            order.verify(createNamespaceUseCase).createNamespace(argThat(
                    (CreateNamespaceCommand c) -> c.getUserId().value().equals(memberId)));
            order.verify(claimPendingFileSharesUseCase).claimPendingFileShares(any());
            then(claimPendingFileSharesUseCase).should().claimPendingFileShares(argThat(
                    (ClaimPendingFileSharesCommand c) -> c.getMemberId().equals(memberId)
                            && c.getGranteeEmail().equals("river@modudrive.com")));
            then(processedEvents).should().markProcessed(MemberQueues.SIGNED_UP, "outbox-1");
        }
    }

    @Nested
    @DisplayName("이미 처리한 메시지가 다시 오면")
    class WhenMessageWasAlreadyHandled {

        @Test
        void skipsItWithoutClaimingAgain() {
            given(processedEvents.claim(MemberQueues.SIGNED_UP, "outbox-1")).willReturn(false);

            listener.onMemberSignedUp(event, "outbox-1");

            then(createNamespaceUseCase).shouldHaveNoInteractions();
            then(claimPendingFileSharesUseCase).shouldHaveNoInteractions();
            then(processedEvents).should(never()).markProcessed(anyString(), anyString());
        }
    }
}
