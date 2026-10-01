package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.out.LoginChallengePort;
import com.moduDrive.auth.application.port.out.LoginChallengePort.CodeRequest;
import com.moduDrive.auth.application.port.out.SendLoginVerificationMailPort;
import com.moduDrive.auth.domain.vo.LoginChallengeId;
import com.moduDrive.auth.domain.vo.MemberEmail;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.common.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SendLoginCodeServiceTest {

    @Mock
    private LoginChallengePort loginChallengePort;
    @Mock
    private SendLoginVerificationMailPort sendLoginVerificationMailPort;
    @InjectMocks
    private SendLoginCodeService sendLoginCodeService;

    private static final MemberEmail EMAIL = new MemberEmail("river@modudrive.com");
    private static final LoginChallengeId CHALLENGE_ID = new LoginChallengeId("challenge-id");

    private static void assertRejectedWith(Throwable thrown, AuthExceptionCase exceptionCase) {
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getExceptionCase())
                .isEqualTo(exceptionCase);
    }

    @Nested
    @DisplayName("기다리는 로그인이 있을 때")
    class WhenChallengeExists {

        @Test
        @DisplayName("발송 횟수를 세고, 새 6자리 코드를 저장한 뒤 그 코드를 메일로 보낸다")
        void issuesAndMailsAFreshCode() {
            given(loginChallengePort.findEmail(CHALLENGE_ID)).willReturn(EMAIL);
            given(loginChallengePort.requestCode(EMAIL)).willReturn(CodeRequest.ALLOWED);

            sendLoginCodeService.sendLoginCode(CHALLENGE_ID);

            ArgumentCaptor<String> issued = ArgumentCaptor.forClass(String.class);
            then(loginChallengePort).should().issueCode(eq(CHALLENGE_ID), issued.capture());
            assertThat(issued.getValue()).matches("\\d{6}");
            then(sendLoginVerificationMailPort).should().sendLoginVerificationMail(EMAIL, issued.getValue());
        }

        @ParameterizedTest
        @CsvSource({"TOO_SOON, LOGIN_CODE_REQUEST_TOO_SOON", "TOO_MANY, TOO_MANY_LOGIN_CODE_REQUESTS"})
        @DisplayName("30초 안의 재요청과 15분 한도 초과를 다른 예외로 알리고, 코드를 바꾸지도 보내지도 않는다")
        void refusesTooSoonOrTooMany(CodeRequest refused, AuthExceptionCase expected) {
            given(loginChallengePort.findEmail(CHALLENGE_ID)).willReturn(EMAIL);
            given(loginChallengePort.requestCode(EMAIL)).willReturn(refused);

            assertRejectedWith(catchThrowable(() -> sendLoginCodeService.sendLoginCode(CHALLENGE_ID)), expected);

            then(loginChallengePort).should(never()).issueCode(any(), any());
            then(sendLoginVerificationMailPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("기다리는 로그인이 없을 때")
    class WhenChallengeIsMissing {

        @Test
        @DisplayName("횟수를 세지도 메일을 보내지도 않는다")
        void answersExpired() {
            willThrow(new BusinessException(AuthExceptionCase.LOGIN_VERIFICATION_EXPIRED))
                    .given(loginChallengePort).findEmail(CHALLENGE_ID);

            assertRejectedWith(catchThrowable(() -> sendLoginCodeService.sendLoginCode(CHALLENGE_ID)),
                    AuthExceptionCase.LOGIN_VERIFICATION_EXPIRED);

            then(loginChallengePort).should(never()).requestCode(any());
            then(sendLoginVerificationMailPort).shouldHaveNoInteractions();
        }
    }
}
