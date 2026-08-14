package com.moduDrive.member.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.member.application.port.in.command.VerifyMemberEmailCommand;
import com.moduDrive.member.application.port.out.EmailVerificationTokenPort;
import com.moduDrive.member.application.port.out.UpdateMemberValidityPort;
import com.moduDrive.member.domain.model.Member.MemberId;
import com.moduDrive.member.exception.MemberExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class VerifyMemberEmailServiceTest {

    @Mock
    private EmailVerificationTokenPort emailVerificationTokenPort;
    @Mock
    private UpdateMemberValidityPort updateMemberValidityPort;
    @InjectMocks
    private VerifyMemberEmailService verifyMemberEmailService;

    private static final String TOKEN = "some-token";
    private final VerifyMemberEmailCommand command = new VerifyMemberEmailCommand(TOKEN);

    @Nested
    @DisplayName("토큰이 유효할 때")
    class WhenTokenIsValid {

        @Test
        void marksMemberEmailVerified() {
            UUID memberId = UUID.randomUUID();
            given(emailVerificationTokenPort.consumeToken(TOKEN)).willReturn(Optional.of(memberId));

            verifyMemberEmailService.verifyMemberEmail(command);

            then(updateMemberValidityPort).should().markEmailVerified(new MemberId(memberId));
        }
    }

    @Nested
    @DisplayName("토큰이 존재하지 않거나 만료됐을 때")
    class WhenTokenIsInvalid {

        @Test
        void throwsBusinessExceptionAndSkipsUpdate() {
            given(emailVerificationTokenPort.consumeToken(TOKEN)).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> verifyMemberEmailService.verifyMemberEmail(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(MemberExceptionCase.INVALID_VERIFICATION_TOKEN);
            then(updateMemberValidityPort).shouldHaveNoInteractions();
        }
    }
}
