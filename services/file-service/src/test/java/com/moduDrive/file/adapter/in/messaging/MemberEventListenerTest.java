package com.moduDrive.file.adapter.in.messaging;

import com.moduDrive.common.event.member.MemberSignedUp;
import com.moduDrive.file.application.port.in.command.ClaimPendingFileSharesCommand;
import com.moduDrive.file.application.port.in.usecase.ClaimPendingFileSharesUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class MemberEventListenerTest {

    @Mock
    private ClaimPendingFileSharesUseCase claimPendingFileSharesUseCase;
    @InjectMocks
    private MemberEventListener memberEventListener;

    @Nested
    @DisplayName("회원가입 이벤트를 수신했을 때")
    class WhenMemberSignedUpReceived {

        @Test
        void delegatesToClaimPendingFileSharesUseCase() {
            UUID memberId = UUID.randomUUID();
            MemberSignedUp event = new MemberSignedUp(memberId, "river@modudrive.com");

            memberEventListener.onMemberSignedUp(event);

            // ClaimPendingFileSharesCommand has no equals() (like the other in-port commands), so
            // capture and assert its fields instead of relying on value equality.
            ArgumentCaptor<ClaimPendingFileSharesCommand> captor =
                    ArgumentCaptor.forClass(ClaimPendingFileSharesCommand.class);
            then(claimPendingFileSharesUseCase).should().claimPendingFileShares(captor.capture());
            assertThat(captor.getValue().getMemberId()).isEqualTo(memberId);
            assertThat(captor.getValue().getGranteeEmail()).isEqualTo("river@modudrive.com");
        }
    }
}
