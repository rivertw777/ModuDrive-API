package com.moduDrive.member.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.member.application.port.in.command.ConfirmEmailVerificationCommand;
import com.moduDrive.member.application.port.out.EmailVerificationTokenPort;
import com.moduDrive.member.exception.MemberExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ConfirmEmailVerificationServiceTest {

    @Mock
    private EmailVerificationTokenPort emailVerificationTokenPort;
    @InjectMocks
    private ConfirmEmailVerificationService confirmEmailVerificationService;

    private final ConfirmEmailVerificationCommand command = new ConfirmEmailVerificationCommand("some-token");

    @Nested
    @DisplayName("유효한 토큰일 때")
    class WhenTokenIsValid {

        @Test
        void marksResolvedEmailAsVerified() {
            given(emailVerificationTokenPort.consumeToken("some-token"))
                    .willReturn(Optional.of("river@modudrive.com"));

            confirmEmailVerificationService.confirmEmailVerification(command);

            then(emailVerificationTokenPort).should().markVerified("river@modudrive.com");
        }
    }

    @Nested
    @DisplayName("유효하지 않은 토큰일 때")
    class WhenTokenIsInvalid {

        @Test
        void throwsBusinessExceptionAndSkipsMarking() {
            given(emailVerificationTokenPort.consumeToken("some-token")).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> confirmEmailVerificationService.confirmEmailVerification(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(MemberExceptionCase.INVALID_VERIFICATION_TOKEN);
            then(emailVerificationTokenPort).should(never()).markVerified(anyString());
        }
    }
}
