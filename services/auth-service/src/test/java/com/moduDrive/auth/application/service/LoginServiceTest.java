package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.LoginCommand;
import com.moduDrive.auth.application.port.out.AuthenticateMemberPort;
import com.moduDrive.auth.application.port.out.CreateSessionPort;
import com.moduDrive.auth.application.port.out.DeleteSessionPort;
import com.moduDrive.auth.application.port.out.KnownDevicePort;
import com.moduDrive.auth.application.port.out.LoginAttemptPort;
import com.moduDrive.auth.application.port.out.LoginChallengePort;
import com.moduDrive.auth.domain.model.LoginResult;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.vo.DeviceId;
import com.moduDrive.auth.domain.vo.LoginChallengeId;
import com.moduDrive.auth.domain.vo.MemberEmail;
import com.moduDrive.auth.domain.vo.MemberPassword;
import com.moduDrive.auth.domain.vo.SessionId;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.auth.fixture.MemberAuthDataTestFixture;
import com.moduDrive.common.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    @InjectMocks
    private LoginService loginService;

    private static final MemberEmail EMAIL = new MemberEmail("river@modudrive.com");
    private static final MemberPassword PASSWORD = new MemberPassword("raw-password");
    private static final SessionId NEW_SESSION_ID = new SessionId("new-session-id");
    private static final SessionId PREVIOUS_SESSION_ID = new SessionId("previous-session-id");
    private static final DeviceId DEVICE_ID = new DeviceId("device-id");
    private static final LoginChallengeId CHALLENGE_ID = new LoginChallengeId("challenge-id");

    private final MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthData();

    /** Password is right; the attempt was counted on {@code countedOn}'s count (null = the shared one). */
    private void givenPasswordMatches(DeviceId countedOn) {
        given(loginAttemptPort.tryAttempt(EMAIL, countedOn)).willReturn(true);
        given(authenticateMemberPort.authenticateMember(EMAIL, PASSWORD)).willReturn(memberAuthData);
    }

    @Nested
    @DisplayName("이미 인증된 기기에서 로그인할 때")
    class WhenDeviceIsKnown {

        @Test
        @DisplayName("그 기기 몫의 시도 횟수로 세고, 세션을 만든 뒤 그 횟수를 지운다")
        void signsInRightAwayCountingOnTheDevice() {
            given(knownDevicePort.isKnown(EMAIL, DEVICE_ID)).willReturn(true);
            givenPasswordMatches(DEVICE_ID);
            given(knownDevicePort.refreshIfKnown("member-id", EMAIL, DEVICE_ID)).willReturn(true);
            given(createSessionPort.createSession(memberAuthData)).willReturn(NEW_SESSION_ID);

            LoginResult result = loginService.login(new LoginCommand(EMAIL, PASSWORD, null, DEVICE_ID));

            assertThat(result).isEqualTo(new LoginResult.SignedIn(NEW_SESSION_ID, DEVICE_ID));
            then(deleteSessionPort).shouldHaveNoInteractions();
            then(loginAttemptPort).should().clearAttempts(EMAIL, DEVICE_ID);
            then(loginChallengePort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("이 브라우저의 이전 세션을 지우고 새 세션을 만든다 (세션 고정 방지)")
        void replacesPreviousSession() {
            given(knownDevicePort.isKnown(EMAIL, DEVICE_ID)).willReturn(true);
            givenPasswordMatches(DEVICE_ID);
            given(knownDevicePort.refreshIfKnown("member-id", EMAIL, DEVICE_ID)).willReturn(true);
            given(createSessionPort.createSession(memberAuthData)).willReturn(NEW_SESSION_ID);

            loginService.login(new LoginCommand(EMAIL, PASSWORD, PREVIOUS_SESSION_ID, DEVICE_ID));

            then(deleteSessionPort).should().deleteSession(PREVIOUS_SESSION_ID);
        }

        @Test
        @DisplayName("이메일이 아직 없는 예전 기기 행이면 공용 횟수로 세고 그걸 지운다")
        void oldRowWithoutEmailCountsOnTheSharedCount() {
            given(knownDevicePort.isKnown(EMAIL, DEVICE_ID)).willReturn(false);
            givenPasswordMatches(null);
            given(knownDevicePort.refreshIfKnown("member-id", EMAIL, DEVICE_ID)).willReturn(true);
            given(createSessionPort.createSession(memberAuthData)).willReturn(NEW_SESSION_ID);

            loginService.login(new LoginCommand(EMAIL, PASSWORD, null, DEVICE_ID));

            then(loginAttemptPort).should().clearAttempts(EMAIL, null);
        }
    }

    @Nested
    @DisplayName("처음 보는 기기에서 로그인할 때")
    class WhenDeviceIsUnknown {

        @Test
        @DisplayName("공용 횟수로 세고, 세션도 메일도 없이 확인을 만들어 코드 요청을 기다린다")
        void waitsForTheEmailedCode() {
            given(knownDevicePort.isKnown(EMAIL, DEVICE_ID)).willReturn(false);
            givenPasswordMatches(null);
            given(knownDevicePort.refreshIfKnown("member-id", EMAIL, DEVICE_ID)).willReturn(false);
            given(loginChallengePort.createChallenge(memberAuthData, EMAIL)).willReturn(CHALLENGE_ID);

            LoginResult result = loginService.login(new LoginCommand(EMAIL, PASSWORD, PREVIOUS_SESSION_ID, DEVICE_ID));

            assertThat(result).isEqualTo(new LoginResult.VerificationRequired(CHALLENGE_ID));
            then(createSessionPort).shouldHaveNoInteractions();
            then(deleteSessionPort).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("시도 횟수는 지우지 않는다 — 코드까지 맞혀야 지워진다")
        void keepsTheAttemptCount() {
            givenPasswordMatches(null);
            given(loginChallengePort.createChallenge(memberAuthData, EMAIL)).willReturn(CHALLENGE_ID);

            loginService.login(new LoginCommand(EMAIL, PASSWORD, null, null));

            then(loginAttemptPort).should(never()).clearAttempts(any(), any());
            then(knownDevicePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("인증에 실패했을 때")
    class WhenAuthenticationFails {

        @Test
        @DisplayName("세션도 코드도 만들지 않고, 기존 세션도 지우지 않는다")
        void leavesEverythingUntouched() {
            given(knownDevicePort.isKnown(EMAIL, DEVICE_ID)).willReturn(false);
            given(loginAttemptPort.tryAttempt(EMAIL, null)).willReturn(true);
            willThrow(new BusinessException(AuthExceptionCase.INVALID_CREDENTIALS))
                    .given(authenticateMemberPort).authenticateMember(EMAIL, PASSWORD);

            Throwable thrown = catchThrowable(() ->
                    loginService.login(new LoginCommand(EMAIL, PASSWORD, PREVIOUS_SESSION_ID, DEVICE_ID)));

            assertThat(thrown).isInstanceOf(BusinessException.class);
            then(createSessionPort).shouldHaveNoInteractions();
            then(deleteSessionPort).shouldHaveNoInteractions();
            then(loginChallengePort).shouldHaveNoInteractions();
            then(loginAttemptPort).should(never()).clearAttempts(any(), any());
        }
    }

    @Nested
    @DisplayName("시도 횟수를 다 썼을 때")
    class WhenAttemptsAreUsedUp {

        @Test
        @DisplayName("비밀번호를 확인하지 않고 거절한다")
        void rejectsWithoutCheckingPassword() {
            given(loginAttemptPort.tryAttempt(EMAIL, null)).willReturn(false);

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
