package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.RevokeMemberAccessCommand;
import com.moduDrive.auth.application.port.out.DeleteSessionPort;
import com.moduDrive.auth.application.port.out.KnownDevicePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RevokeMemberAccessServiceTest {

    @Mock
    private KnownDevicePort knownDevicePort;
    @Mock
    private DeleteSessionPort deleteSessionPort;
    @InjectMocks
    private RevokeMemberAccessService revokeMemberAccessService;

    @Nested
    @DisplayName("비밀번호가 바뀐 회원이면")
    class WhenPasswordChanged {

        @Test
        @DisplayName("인증된 기기를 모두 잊고 세션을 모두 끝낸다")
        void forgetsDevicesAndEndsSessions() {
            revokeMemberAccessService.revokeMemberAccess(new RevokeMemberAccessCommand("member-id"));

            then(knownDevicePort).should().forgetAll("member-id");
            then(deleteSessionPort).should().deleteAllSessions("member-id");
        }
    }
}
