package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.ValidateSessionCommand;
import com.moduDrive.auth.application.port.out.FindSessionPort;
import com.moduDrive.auth.domain.model.MemberAuthData;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ValidateSessionServiceTest {

    @Mock
    private FindSessionPort findSessionPort;
    @InjectMocks
    private ValidateSessionService validateSessionService;

    private static final SessionId SESSION_ID = new SessionId("session-id");

    @Nested
    @DisplayName("살아 있는 세션일 때")
    class WhenSessionIsLive {

        @Test
        void returnsMemberAuthDataPassingTouchThrough() {
            MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthData();
            given(findSessionPort.findSession(SESSION_ID, false)).willReturn(Optional.of(memberAuthData));

            MemberAuthData result = validateSessionService.validateSession(new ValidateSessionCommand(SESSION_ID, false));

            assertThat(result).isSameAs(memberAuthData);
        }
    }

    @Nested
    @DisplayName("세션이 없거나 만료되었을 때")
    class WhenSessionIsMissing {

        @Test
        void throwsSessionNotFound() {
            given(findSessionPort.findSession(SESSION_ID, true)).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() ->
                    validateSessionService.validateSession(new ValidateSessionCommand(SESSION_ID, true)));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(AuthExceptionCase.SESSION_NOT_FOUND);
        }
    }
}
