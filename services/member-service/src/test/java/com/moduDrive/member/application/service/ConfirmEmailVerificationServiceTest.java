package com.moduDrive.member.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.member.application.port.in.command.ConfirmEmailVerificationCommand;
import com.moduDrive.member.application.port.out.EmailVerificationTokenPort;
import com.moduDrive.member.domain.model.Member.MemberEmail;
import com.moduDrive.member.exception.MemberExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ConfirmEmailVerificationServiceTest {

    private static final String EMAIL = "river@modudrive.com";
    private static final String CODE = "042917";

    @Mock
    private EmailVerificationTokenPort emailVerificationTokenPort;
    @InjectMocks
    private ConfirmEmailVerificationService confirmEmailVerificationService;

    private final ConfirmEmailVerificationCommand command =
            new ConfirmEmailVerificationCommand(new MemberEmail(EMAIL), CODE);

    @Nested
    @DisplayName("인증 코드가 일치할 때")
    class WhenCodeMatches {

        @Test
        void marksEmailAsVerified() {
            given(emailVerificationTokenPort.confirmCode(EMAIL, CODE)).willReturn(true);

            confirmEmailVerificationService.confirmEmailVerification(command);

            then(emailVerificationTokenPort).should().markVerified(EMAIL);
        }
    }

    @Nested
    @DisplayName("인증 코드가 일치하지 않을 때")
    class WhenCodeDoesNotMatch {

        @Test
        void throwsBusinessExceptionAndSkipsMarking() {
            given(emailVerificationTokenPort.confirmCode(EMAIL, CODE)).willReturn(false);

            Throwable thrown = catchThrowable(() -> confirmEmailVerificationService.confirmEmailVerification(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(MemberExceptionCase.INVALID_VERIFICATION_CODE);
            then(emailVerificationTokenPort).should(never()).markVerified(anyString());
        }
    }
}
