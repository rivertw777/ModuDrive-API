package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.LogoutCommand;
import com.moduDrive.auth.application.port.out.BlacklistAccessTokenPort;
import com.moduDrive.auth.application.port.out.RevokeRefreshTokenPort;
import com.moduDrive.auth.application.port.out.ValidateTokenPort;
import com.moduDrive.auth.domain.model.AccessTokenClaims;
import com.moduDrive.auth.domain.model.RefreshTokenClaims;
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
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {

    @Mock
    private ValidateTokenPort validateTokenPort;
    @Mock
    private RevokeRefreshTokenPort revokeRefreshTokenPort;
    @Mock
    private BlacklistAccessTokenPort blacklistAccessTokenPort;
    @InjectMocks
    private LogoutService logoutService;

    private static final AccessToken ACCESS_TOKEN = new AccessToken("access-token");
    private static final TokenJti ACCESS_JTI = new TokenJti("access-jti");
    private static final Date EXPIRES_AT = new Date(System.currentTimeMillis() + 60_000L);

    private final RefreshTokenClaims refreshClaims = RefreshTokenClaims.create(
            MemberAuthDataTestFixture.aMemberAuthData(),
            TokenPairTestFixture.DEFAULT_FAMILY_ID,
            new TokenJti("refresh-jti"));

    private LogoutCommand commandWithAccessToken() {
        return new LogoutCommand(TokenPairTestFixture.DEFAULT_REFRESH_TOKEN, ACCESS_TOKEN);
    }

    private LogoutCommand commandWithoutAccessToken() {
        return new LogoutCommand(TokenPairTestFixture.DEFAULT_REFRESH_TOKEN, null);
    }

    @Nested
    @DisplayName("리프레시 토큰과 유효한 액세스 토큰이 함께 주어졌을 때")
    class WhenBothTokensAreValid {

        @Test
        void revokesFamilyAndBlacklistsAccessToken() {
            LogoutCommand command = commandWithAccessToken();
            given(validateTokenPort.getRefreshTokenClaims(command.getRefreshToken())).willReturn(refreshClaims);
            given(validateTokenPort.getAccessTokenClaims(ACCESS_TOKEN)).willReturn(
                    AccessTokenClaims.create(MemberAuthDataTestFixture.aMemberAuthData(), ACCESS_JTI,
                            TokenPairTestFixture.DEFAULT_FAMILY_ID, EXPIRES_AT));

            logoutService.logout(command);

            then(revokeRefreshTokenPort).should().revoke(TokenPairTestFixture.DEFAULT_FAMILY_ID);
            then(blacklistAccessTokenPort).should().blacklist(ACCESS_JTI, EXPIRES_AT);
        }
    }

    @Nested
    @DisplayName("액세스 토큰 없이 리프레시 토큰만 주어졌을 때")
    class WhenAccessTokenIsAbsent {

        @Test
        void revokesFamilyWithoutBlacklisting() {
            LogoutCommand command = commandWithoutAccessToken();
            given(validateTokenPort.getRefreshTokenClaims(command.getRefreshToken())).willReturn(refreshClaims);

            logoutService.logout(command);

            then(revokeRefreshTokenPort).should().revoke(TokenPairTestFixture.DEFAULT_FAMILY_ID);
            then(blacklistAccessTokenPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("액세스 토큰이 이미 만료되었거나 유효하지 않을 때")
    class WhenAccessTokenIsAlreadyInvalid {

        @Test
        void stillSucceedsAfterRevokingFamily() {
            LogoutCommand command = commandWithAccessToken();
            given(validateTokenPort.getRefreshTokenClaims(command.getRefreshToken())).willReturn(refreshClaims);
            willThrow(new BusinessException(AuthExceptionCase.TOKEN_EXPIRED))
                    .given(validateTokenPort).getAccessTokenClaims(ACCESS_TOKEN);

            logoutService.logout(command);

            then(revokeRefreshTokenPort).should().revoke(TokenPairTestFixture.DEFAULT_FAMILY_ID);
            then(blacklistAccessTokenPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("리프레시 토큰이 유효하지 않을 때")
    class WhenRefreshTokenIsInvalid {

        @Test
        void throwsBusinessExceptionWithoutRevoking() {
            LogoutCommand command = commandWithAccessToken();
            willThrow(new BusinessException(AuthExceptionCase.TOKEN_INVALID))
                    .given(validateTokenPort).getRefreshTokenClaims(command.getRefreshToken());

            Throwable thrown = catchThrowable(() -> logoutService.logout(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(AuthExceptionCase.TOKEN_INVALID);
            then(revokeRefreshTokenPort).shouldHaveNoInteractions();
            then(blacklistAccessTokenPort).shouldHaveNoInteractions();
        }
    }
}
