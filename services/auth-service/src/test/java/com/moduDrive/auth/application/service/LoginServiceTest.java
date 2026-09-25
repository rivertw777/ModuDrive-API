package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.LoginCommand;
import com.moduDrive.auth.application.port.out.AuthenticateMemberPort;
import com.moduDrive.auth.application.port.out.CreateSessionPort;
import com.moduDrive.auth.application.port.out.DeleteSessionPort;
import com.moduDrive.auth.domain.model.MemberAuthData;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private AuthenticateMemberPort authenticateMemberPort;
    @Mock
    private CreateSessionPort createSessionPort;
    @Mock
    private DeleteSessionPort deleteSessionPort;
    @InjectMocks
    private LoginService loginService;

    private static final MemberEmail EMAIL = new MemberEmail("river@modudrive.com");
    private static final MemberPassword PASSWORD = new MemberPassword("raw-password");
    private static final AuthenticateMemberRequest AUTHENTICATE_REQUEST =
            new AuthenticateMemberRequest(EMAIL.value(), PASSWORD.value());
    private static final SessionId NEW_SESSION_ID = new SessionId("new-session-id");
    private static final SessionId PREVIOUS_SESSION_ID = new SessionId("previous-session-id");

    private final MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthData();

    @Nested
    @DisplayName("세션 쿠키 없이 인증에 성공했을 때")
    class WhenAuthenticationSucceedsWithoutPreviousSession {

        @Test
        void createsSessionForMember() {
            given(authenticateMemberPort.authenticateMember(AUTHENTICATE_REQUEST)).willReturn(memberAuthData);
            given(createSessionPort.createSession(memberAuthData)).willReturn(NEW_SESSION_ID);

            SessionId result = loginService.login(new LoginCommand(EMAIL, PASSWORD, null));

            assertThat(result).isEqualTo(NEW_SESSION_ID);
            then(deleteSessionPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("기존 세션 쿠키를 가진 채 인증에 성공했을 때")
    class WhenAuthenticationSucceedsWithPreviousSession {

        @Test
        @DisplayName("이전 세션을 지우고 새 세션을 만든다 (세션 고정 방지)")
        void replacesPreviousSession() {
            given(authenticateMemberPort.authenticateMember(AUTHENTICATE_REQUEST)).willReturn(memberAuthData);
            given(createSessionPort.createSession(memberAuthData)).willReturn(NEW_SESSION_ID);

            SessionId result = loginService.login(new LoginCommand(EMAIL, PASSWORD, PREVIOUS_SESSION_ID));

            assertThat(result).isEqualTo(NEW_SESSION_ID);
            then(deleteSessionPort).should().deleteSession(PREVIOUS_SESSION_ID);
        }
    }

    @Nested
    @DisplayName("인증에 실패했을 때")
    class WhenAuthenticationFails {

        @Test
        @DisplayName("세션을 만들지도, 기존 세션을 지우지도 않는다")
        void leavesSessionsUntouched() {
            willThrow(new BusinessException(AuthExceptionCase.MEMBER_NOT_VALID))
                    .given(authenticateMemberPort).authenticateMember(AUTHENTICATE_REQUEST);

            Throwable thrown = catchThrowable(() ->
                    loginService.login(new LoginCommand(EMAIL, PASSWORD, PREVIOUS_SESSION_ID)));

            assertThat(thrown).isInstanceOf(BusinessException.class);
            then(createSessionPort).shouldHaveNoInteractions();
            then(deleteSessionPort).shouldHaveNoInteractions();
        }
    }
}
