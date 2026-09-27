package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.LoginCommand;
import com.moduDrive.auth.application.port.out.AuthenticateMemberPort;
import com.moduDrive.auth.application.port.out.CreateSessionPort;
import com.moduDrive.auth.application.port.out.DeleteSessionPort;
import com.moduDrive.auth.application.port.out.KnownDevicePort;
import com.moduDrive.auth.application.port.out.LoginAttemptPort;
import com.moduDrive.auth.application.port.out.LoginChallengePort;
import com.moduDrive.auth.application.port.out.SendLoginVerificationMailPort;
import com.moduDrive.auth.domain.model.LoginResult;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.vo.DeviceId;
import com.moduDrive.auth.domain.vo.LoginChallengeId;
import com.moduDrive.auth.domain.vo.MemberEmail;
import com.moduDrive.auth.domain.vo.MemberPassword;
import com.moduDrive.auth.domain.vo.SessionId;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.auth.fixture.MemberAuthDataTestFixture;
import com.moduDrive.common.api.dto.member.AuthenticateMemberRequest;
import com.moduDrive.common.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private AuthenticateMemberPort authenticateMemberPort;
    @Mock
    private CreateSessionPort createSessionPort;
    @Mock
    private DeleteSessionPort deleteSessionPort;
    @Mock
    private LoginAttemptPort loginAttemptPort;
    @Mock
    private KnownDevicePort knownDevicePort;
    @Mock
    private LoginChallengePort loginChallengePort;
    @Mock
    private SendLoginVerificationMailPort sendLoginVerificationMailPort;
    @InjectMocks
    private LoginService loginService;

    private static final MemberEmail EMAIL = new MemberEmail("river@modudrive.com");
    private static final MemberPassword PASSWORD = new MemberPassword("raw-password");
    private static final AuthenticateMemberRequest AUTHENTICATE_REQUEST =
            new AuthenticateMemberRequest(EMAIL.value(), PASSWORD.value());
    private static final SessionId NEW_SESSION_ID = new SessionId("new-session-id");
    private static final SessionId PREVIOUS_SESSION_ID = new SessionId("previous-session-id");
    private static final DeviceId DEVICE_ID = new DeviceId("device-id");
    private static final LoginChallengeId CHALLENGE_ID = new LoginChallengeId("challenge-id");

    private final MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthData();

    private void givenPasswordMatches() {
        given(loginAttemptPort.tryAttempt(EMAIL)).willReturn(true);
        given(authenticateMemberPort.authenticateMember(AUTHENTICATE_REQUEST)).willReturn(memberAuthData);
    }

    @Nested
    @DisplayName("이미 인증된 기기에서 로그인할 때")
    class WhenDeviceIsKnown {

        @Test
        @DisplayName("세션을 만들고 기기 ID를 돌려주며 시도 횟수를 지운다")
        void signsInRightAway() {
            givenPasswordMatches();
            given(knownDevicePort.refreshIfKnown("member-id", DEVICE_ID)).willReturn(true);
            given(createSessionPort.createSession(memberAuthData)).willReturn(NEW_SESSION_ID);

            LoginResult result = loginService.login(new LoginCommand(EMAIL, PASSWORD, null, DEVICE_ID));

            assertThat(result).isEqualTo(new LoginResult.SignedIn(NEW_SESSION_ID, DEVICE_ID));
            then(deleteSessionPort).shouldHaveNoInteractions();
            then(loginAttemptPort).should().clearAttempts(EMAIL);
            then(loginChallengePort).shouldHaveNoInteractions();
            then(sendLoginVerificationMailPort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("이 브라우저의 이전 세션을 지우고 새 세션을 만든다 (세션 고정 방지)")
        void replacesPreviousSession() {
            givenPasswordMatches();
            given(knownDevicePort.refreshIfKnown("member-id", DEVICE_ID)).willReturn(true);
            given(createSessionPort.createSession(memberAuthData)).willReturn(NEW_SESSION_ID);

            loginService.login(new LoginCommand(EMAIL, PASSWORD, PREVIOUS_SESSION_ID, DEVICE_ID));

            then(deleteSessionPort).should().deleteSession(PREVIOUS_SESSION_ID);
        }
    }

    @Nested
    @DisplayName("처음 보는 기기에서 로그인할 때")
    class WhenDeviceIsUnknown {

        @Test
        @DisplayName("세션 없이 6자리 코드를 저장·메일로 보내고 확인을 기다린다")
        void waitsForTheEmailedCode() {
            givenPasswordMatches();
            given(knownDevicePort.refreshIfKnown("member-id", DEVICE_ID)).willReturn(false);
            given(loginChallengePort.createChallenge(eq(memberAuthData), eq(EMAIL), anyString())).willReturn(CHALLENGE_ID);

            LoginResult result = loginService.login(new LoginCommand(EMAIL, PASSWORD, PREVIOUS_SESSION_ID, DEVICE_ID));

            assertThat(result).isEqualTo(new LoginResult.VerificationRequired(CHALLENGE_ID));
            ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
            then(loginChallengePort).should().createChallenge(eq(memberAuthData), eq(EMAIL), stored.capture());
            assertThat(stored.getValue()).matches("\\d{6}");
            then(sendLoginVerificationMailPort).should().sendLoginVerificationMail("member-id", stored.getValue());
            then(createSessionPort).shouldHaveNoInteractions();
            then(deleteSessionPort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("시도 횟수는 지우지 않는다 — 코드까지 맞혀야 지워진다")
        void keepsTheAttemptCount() {
            givenPasswordMatches();
            given(loginChallengePort.createChallenge(eq(memberAuthData), eq(EMAIL), anyString())).willReturn(CHALLENGE_ID);

            loginService.login(new LoginCommand(EMAIL, PASSWORD, null, null));

            then(loginAttemptPort).should(never()).clearAttempts(EMAIL);
            then(knownDevicePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("인증에 실패했을 때")
    class WhenAuthenticationFails {

        @Test
        @DisplayName("세션도 코드도 만들지 않고, 기존 세션도 지우지 않는다")
        void leavesEverythingUntouched() {
            given(loginAttemptPort.tryAttempt(EMAIL)).willReturn(true);
            willThrow(new BusinessException(AuthExceptionCase.INVALID_CREDENTIALS))
                    .given(authenticateMemberPort).authenticateMember(AUTHENTICATE_REQUEST);

            Throwable thrown = catchThrowable(() ->
                    loginService.login(new LoginCommand(EMAIL, PASSWORD, PREVIOUS_SESSION_ID, DEVICE_ID)));

            assertThat(thrown).isInstanceOf(BusinessException.class);
            then(createSessionPort).shouldHaveNoInteractions();
            then(deleteSessionPort).shouldHaveNoInteractions();
            then(loginChallengePort).shouldHaveNoInteractions();
            then(loginAttemptPort).should(never()).clearAttempts(EMAIL);
        }
    }

    @Nested
    @DisplayName("시도 횟수를 다 썼을 때")
    class WhenAttemptsAreUsedUp {

        @Test
        @DisplayName("비밀번호를 확인하지 않고 거절한다")
        void rejectsWithoutCheckingPassword() {
            given(loginAttemptPort.tryAttempt(EMAIL)).willReturn(false);

            Throwable thrown = catchThrowable(() -> loginService.login(new LoginCommand(EMAIL, PASSWORD, null, null)));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(AuthExceptionCase.TOO_MANY_LOGIN_ATTEMPTS);
            then(authenticateMemberPort).shouldHaveNoInteractions();
            then(createSessionPort).shouldHaveNoInteractions();
        }
    }
}
