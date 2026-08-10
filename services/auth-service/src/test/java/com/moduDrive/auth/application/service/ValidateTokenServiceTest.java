package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.ValidateTokenCommand;
import com.moduDrive.auth.application.port.out.IsAccessTokenBlacklistedPort;
import com.moduDrive.auth.application.port.out.IsFamilyRevokedPort;
import com.moduDrive.auth.application.port.out.ValidateTokenPort;
import com.moduDrive.auth.domain.model.AccessTokenClaims;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.model.TokenPair.AccessToken;
import com.moduDrive.auth.domain.model.TokenPair.TokenJti;
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

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ValidateTokenServiceTest {

    @Mock
    private ValidateTokenPort validateTokenPort;
    @Mock
    private IsAccessTokenBlacklistedPort isAccessTokenBlacklistedPort;
    @Mock
    private IsFamilyRevokedPort isFamilyRevokedPort;
    @InjectMocks
    private ValidateTokenService validateTokenService;

    private static final TokenJti ACCESS_JTI = new TokenJti("access-jti");

    private final AccessToken accessToken = new AccessToken("access-token");
    private final ValidateTokenCommand command = new ValidateTokenCommand(accessToken);

    private final MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthData();
    private final AccessTokenClaims claims = AccessTokenClaims.create(
            memberAuthData, ACCESS_JTI, TokenPairTestFixture.DEFAULT_FAMILY_ID,
            new Date(System.currentTimeMillis() + 60_000L));

    @Nested
    @DisplayName("블랙리스트에도 없고 패밀리도 폐기되지 않은 토큰일 때")
    class WhenTokenIsNeitherBlacklistedNorRevoked {

        @Test
        void returnsMemberAuthDataFromClaims() {
            given(validateTokenPort.getAccessTokenClaims(accessToken)).willReturn(claims);
            given(isAccessTokenBlacklistedPort.isBlacklisted(ACCESS_JTI)).willReturn(false);
            given(isFamilyRevokedPort.isRevoked(TokenPairTestFixture.DEFAULT_FAMILY_ID)).willReturn(false);

            MemberAuthData result = validateTokenService.validateToken(command);

            assertThat(result).isEqualTo(memberAuthData);
        }
    }

    @Nested
    @DisplayName("로그아웃되어 jti가 블랙리스트에 등록된 토큰일 때")
    class WhenTokenJtiIsBlacklisted {

        @Test
        void throwsAccessTokenRevokedException() {
            given(validateTokenPort.getAccessTokenClaims(accessToken)).willReturn(claims);
            given(isAccessTokenBlacklistedPort.isBlacklisted(ACCESS_JTI)).willReturn(true);

            Throwable thrown = catchThrowable(() -> validateTokenService.validateToken(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(AuthExceptionCase.ACCESS_TOKEN_REVOKED);
        }
    }

    @Nested
    @DisplayName("jti는 블랙리스트에 없지만 토큰 패밀리가 폐기되었을 때")
    class WhenTokenFamilyIsRevoked {

        @Test
        void throwsAccessTokenRevokedException() {
            given(validateTokenPort.getAccessTokenClaims(accessToken)).willReturn(claims);
            given(isAccessTokenBlacklistedPort.isBlacklisted(ACCESS_JTI)).willReturn(false);
            given(isFamilyRevokedPort.isRevoked(TokenPairTestFixture.DEFAULT_FAMILY_ID)).willReturn(true);

            Throwable thrown = catchThrowable(() -> validateTokenService.validateToken(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(AuthExceptionCase.ACCESS_TOKEN_REVOKED);
        }
    }
}
