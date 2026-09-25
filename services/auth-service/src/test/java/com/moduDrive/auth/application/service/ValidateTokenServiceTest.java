package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.ValidateTokenCommand;
import com.moduDrive.auth.application.port.out.IsFamilyRevokedPort;
import com.moduDrive.auth.application.port.out.ValidateTokenPort;
import com.moduDrive.auth.domain.model.AccessTokenClaims;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.model.TokenPair.AccessToken;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.auth.fixture.MemberAuthDataTestFixture;
import com.moduDrive.auth.fixture.TokenPairTestFixture;
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

@ExtendWith(MockitoExtension.class)
class ValidateTokenServiceTest {

    @Mock
    private ValidateTokenPort validateTokenPort;
    @Mock
    private IsFamilyRevokedPort isFamilyRevokedPort;
    @InjectMocks
    private ValidateTokenService validateTokenService;

    private final AccessToken accessToken = new AccessToken("access-token");
    private final ValidateTokenCommand command = new ValidateTokenCommand(accessToken);

    private final MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthData();
    private final AccessTokenClaims claims = AccessTokenClaims.create(
            memberAuthData, TokenPairTestFixture.DEFAULT_FAMILY_ID);

    @Nested
    @DisplayName("토큰 패밀리가 폐기되지 않았을 때")
    class WhenTokenFamilyIsNotRevoked {

        @Test
        void returnsMemberAuthDataFromClaims() {
            given(validateTokenPort.getAccessTokenClaims(accessToken)).willReturn(claims);
            given(isFamilyRevokedPort.isRevoked(TokenPairTestFixture.DEFAULT_FAMILY_ID)).willReturn(false);

            MemberAuthData result = validateTokenService.validateToken(command);

            assertThat(result).isEqualTo(memberAuthData);
        }
    }

    @Nested
    @DisplayName("토큰 패밀리가 폐기되었을 때 (로그아웃·재사용 감지)")
    class WhenTokenFamilyIsRevoked {

        @Test
        void throwsAccessTokenRevokedException() {
            given(validateTokenPort.getAccessTokenClaims(accessToken)).willReturn(claims);
            given(isFamilyRevokedPort.isRevoked(TokenPairTestFixture.DEFAULT_FAMILY_ID)).willReturn(true);

            Throwable thrown = catchThrowable(() -> validateTokenService.validateToken(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(AuthExceptionCase.ACCESS_TOKEN_REVOKED);
        }
    }
}
