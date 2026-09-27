package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.VerifyLoginCommand;
import com.moduDrive.auth.application.port.out.CreateSessionPort;
import com.moduDrive.auth.application.port.out.DeleteSessionPort;
import com.moduDrive.auth.application.port.out.KnownDevicePort;
import com.moduDrive.auth.application.port.out.LoginAttemptPort;
import com.moduDrive.auth.application.port.out.LoginChallengePort;
import com.moduDrive.auth.domain.model.LoginChallenge;
import com.moduDrive.auth.domain.model.LoginResult;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.vo.DeviceId;
import com.moduDrive.auth.domain.vo.LoginChallengeId;
import com.moduDrive.auth.domain.vo.MemberEmail;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class VerifyLoginServiceTest {

    @Mock
    private LoginChallengePort loginChallengePort;
    @Mock
    private KnownDevicePort knownDevicePort;
    @Mock
    private LoginAttemptPort loginAttemptPort;
    @Mock
    private DeleteSessionPort deleteSessionPort;
    @Mock
    private CreateSessionPort createSessionPort;
    @InjectMocks
    private VerifyLoginService verifyLoginService;

    private static final LoginChallengeId CHALLENGE_ID = new LoginChallengeId("challenge-id");
    private static final MemberEmail EMAIL = new MemberEmail("river@modudrive.com");
    private static final DeviceId DEVICE_ID = new DeviceId("device-id");
    private static final SessionId NEW_SESSION_ID = new SessionId("new-session-id");
    private static final SessionId PREVIOUS_SESSION_ID = new SessionId("previous-session-id");

    private final MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthData();

    @Nested
    @DisplayName("코드가 맞을 때")
    class WhenCodeMatches {

        @Test
        @DisplayName("기기를 기억하고, 시도 횟수·이전 세션을 지우고 새 세션을 만든다")
        void signsInAndRemembersTheDevice() {
            given(loginChallengePort.confirmChallenge(CHALLENGE_ID, "042917"))
                    .willReturn(new LoginChallenge(memberAuthData, EMAIL));
            given(knownDevicePort.remember("member-id", null)).willReturn(DEVICE_ID);
            given(createSessionPort.createSession(memberAuthData)).willReturn(NEW_SESSION_ID);

            LoginResult.SignedIn result = verifyLoginService.verifyLogin(
                    new VerifyLoginCommand(CHALLENGE_ID, "042917", null, PREVIOUS_SESSION_ID));

            assertThat(result).isEqualTo(new LoginResult.SignedIn(NEW_SESSION_ID, DEVICE_ID));
            then(loginAttemptPort).should().clearAttempts(EMAIL);
            then(deleteSessionPort).should().deleteSession(PREVIOUS_SESSION_ID);
        }

        @Test
        @DisplayName("브라우저에 기기 ID가 있으면 그 ID로 기억한다")
        void keepsTheBrowsersDeviceId() {
            given(loginChallengePort.confirmChallenge(CHALLENGE_ID, "042917"))
                    .willReturn(new LoginChallenge(memberAuthData, EMAIL));
            given(knownDevicePort.remember("member-id", DEVICE_ID)).willReturn(DEVICE_ID);
            given(createSessionPort.createSession(memberAuthData)).willReturn(NEW_SESSION_ID);

            verifyLoginService.verifyLogin(new VerifyLoginCommand(CHALLENGE_ID, "042917", DEVICE_ID, null));

            then(knownDevicePort).should().remember("member-id", DEVICE_ID);
            then(deleteSessionPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("코드가 틀리거나 확인 시간이 지났을 때")
    class WhenCodeIsRejected {

        @Test
        @DisplayName("기기도 세션도 건드리지 않는다")
        void leavesEverythingUntouched() {
            willThrow(new BusinessException(AuthExceptionCase.INVALID_LOGIN_VERIFICATION_CODE))
                    .given(loginChallengePort).confirmChallenge(CHALLENGE_ID, "000000");

            Throwable thrown = catchThrowable(() -> verifyLoginService.verifyLogin(
                    new VerifyLoginCommand(CHALLENGE_ID, "000000", DEVICE_ID, PREVIOUS_SESSION_ID)));

            assertThat(thrown).isInstanceOf(BusinessException.class);
            then(knownDevicePort).shouldHaveNoInteractions();
            then(loginAttemptPort).shouldHaveNoInteractions();
            then(deleteSessionPort).shouldHaveNoInteractions();
            then(createSessionPort).shouldHaveNoInteractions();
        }
    }
}
