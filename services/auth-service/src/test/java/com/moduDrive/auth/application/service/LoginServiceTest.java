package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.LoginCommand;
import com.moduDrive.auth.application.port.out.AuthenticateMemberPort;
import com.moduDrive.auth.application.port.out.GenerateTokenPort;
import com.moduDrive.auth.application.port.out.SaveRefreshTokenPort;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.model.TokenPair;
import com.moduDrive.auth.domain.vo.MemberEmail;
import com.moduDrive.auth.domain.vo.MemberPassword;
import com.moduDrive.auth.fixture.MemberAuthDataTestFixture;
import com.moduDrive.auth.fixture.TokenPairTestFixture;
import com.moduDrive.common.api.dto.member.AuthenticateMemberRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private AuthenticateMemberPort authenticateMemberPort;
    @Mock
    private GenerateTokenPort generateTokenPort;
    @Mock
    private SaveRefreshTokenPort saveRefreshTokenPort;
    @InjectMocks
    private LoginService loginService;

    private final LoginCommand command = new LoginCommand(
            new MemberEmail("river@modudrive.com"),
            new MemberPassword("raw-password"));

    @Nested
    @DisplayName("인증에 성공했을 때")
    class WhenAuthenticationSucceeds {

        @Test
        void returnsGeneratedTokenPair() {
            MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthData();
            TokenPair tokenPair = TokenPairTestFixture.aTokenPair();
            given(authenticateMemberPort.authenticateMember(
                    new AuthenticateMemberRequest(command.getMemberEmail().value(), command.getMemberPassword().value())))
                    .willReturn(memberAuthData);
            given(generateTokenPort.generateToken(memberAuthData)).willReturn(tokenPair);

            TokenPair result = loginService.login(command);

            assertThat(result).isEqualTo(tokenPair);
            then(saveRefreshTokenPort).should().save(
                    TokenPairTestFixture.DEFAULT_FAMILY_ID, TokenPairTestFixture.DEFAULT_JTI);
        }
    }
}
