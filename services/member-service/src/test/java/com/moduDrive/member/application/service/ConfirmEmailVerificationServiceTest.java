package com.moduDrive.member.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.member.application.port.in.command.ConfirmEmailVerificationCommand;
import com.moduDrive.member.application.port.out.EmailVerificationTokenPort;
import com.moduDrive.member.application.port.out.EmailVerificationTokenPort.CodeConfirmation;
import com.moduDrive.member.domain.model.Member.MemberEmail;
import com.moduDrive.member.exception.MemberExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
            given(emailVerificationTokenPort.confirmCode(EMAIL, CODE)).willReturn(CodeConfirmation.MATCHED);

            confirmEmailVerificationService.confirmEmailVerification(command);

            then(emailVerificationTokenPort).should().markVerified(EMAIL);
        }
    }

    @Nested
    @DisplayName("인증 코드가 일치하지 않을 때")
    class WhenCodeDoesNotMatch {

        @Test
        void throwsBusinessExceptionAndSkipsMarking() {
            given(emailVerificationTokenPort.confirmCode(EMAIL, CODE)).willReturn(CodeConfirmation.MISMATCHED);

            Throwable thrown = catchThrowable(() -> confirmEmailVerificationService.confirmEmailVerification(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(MemberExceptionCase.INVALID_VERIFICATION_CODE);
            then(emailVerificationTokenPort).should(never()).markVerified(anyString());
        }
    }

    @Nested
    @DisplayName("인증 코드가 만료됐거나 입력 횟수를 다 썼을 때")
    class WhenCodeHasEnded {

        @ParameterizedTest
        @CsvSource({"EXPIRED, VERIFICATION_CODE_EXPIRED", "EXHAUSTED, VERIFICATION_ATTEMPTS_EXCEEDED"})
        @DisplayName("만료와 입력 횟수 초과를 다른 예외로 알리고, 인증 완료로 표시하지 않는다")
        void throwsCodeEndedAndSkipsMarking(CodeConfirmation confirmation, MemberExceptionCase expected) {
            given(emailVerificationTokenPort.confirmCode(EMAIL, CODE)).willReturn(confirmation);

            Throwable thrown = catchThrowable(() -> confirmEmailVerificationService.confirmEmailVerification(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(expected);
            then(emailVerificationTokenPort).should(never()).markVerified(anyString());
        }
    }
}
