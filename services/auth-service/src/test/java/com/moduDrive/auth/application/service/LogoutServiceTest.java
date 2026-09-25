package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.LogoutCommand;
import com.moduDrive.auth.application.port.out.DeleteSessionPort;
import com.moduDrive.auth.domain.vo.SessionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {

    @Mock
    private DeleteSessionPort deleteSessionPort;
    @InjectMocks
    private LogoutService logoutService;

    @Nested
    @DisplayName("세션 ID가 주어졌을 때")
    class WhenSessionIdIsGiven {

        @Test
        void deletesSession() {
            SessionId sessionId = new SessionId("session-id");

            logoutService.logout(new LogoutCommand(sessionId));

            then(deleteSessionPort).should().deleteSession(sessionId);
        }
    }
}
