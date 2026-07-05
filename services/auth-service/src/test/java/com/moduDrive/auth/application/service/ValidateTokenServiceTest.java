package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.ValidateTokenCommand;
import com.moduDrive.auth.application.port.out.ValidateTokenPort;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.model.TokenPair.AccessToken;
import com.moduDrive.auth.fixture.MemberAuthDataTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ValidateTokenServiceTest {

    @Mock
    private ValidateTokenPort validateTokenPort;
    @InjectMocks
    private ValidateTokenService validateTokenService;

    private final AccessToken accessToken = new AccessToken("access-token");
    private final ValidateTokenCommand command = new ValidateTokenCommand(accessToken);

    @Nested
    @DisplayName("유효한 토큰일 때")
    class WhenTokenIsValid {

        @Test
        void returnsMemberAuthDataFromPort() {
            MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthData();
            given(validateTokenPort.getMemberAuthDataFromToken(accessToken)).willReturn(memberAuthData);

            MemberAuthData result = validateTokenService.validateToken(command);

            assertThat(result).isEqualTo(memberAuthData);
        }
    }
}
