package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.LogoutCommand;
import com.moduDrive.auth.application.port.out.RevokeRefreshTokenPort;
import com.moduDrive.auth.application.port.out.ValidateTokenPort;
import com.moduDrive.auth.domain.model.RefreshTokenClaims;
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
    @InjectMocks
    private LogoutService logoutService;

    private final LogoutCommand command = new LogoutCommand(TokenPairTestFixture.DEFAULT_REFRESH_TOKEN);

    @Nested
    @DisplayName("유효한 리프레시 토큰이 주어졌을 때")
    class WhenRefreshTokenIsValid {

        @Test
        void revokesTokenFamily() {
            given(validateTokenPort.getRefreshTokenClaims(command.getRefreshToken())).willReturn(
                    RefreshTokenClaims.create(
                            MemberAuthDataTestFixture.aMemberAuthData(),
                            TokenPairTestFixture.DEFAULT_FAMILY_ID,
                            new TokenJti("refresh-jti")));

            logoutService.logout(command);

            then(revokeRefreshTokenPort).should().revoke(TokenPairTestFixture.DEFAULT_FAMILY_ID);
        }
    }

    @Nested
    @DisplayName("리프레시 토큰이 유효하지 않을 때")
    class WhenRefreshTokenIsInvalid {

        @Test
        void throwsBusinessExceptionWithoutRevoking() {
            willThrow(new BusinessException(AuthExceptionCase.TOKEN_INVALID))
                    .given(validateTokenPort).getRefreshTokenClaims(command.getRefreshToken());

            Throwable thrown = catchThrowable(() -> logoutService.logout(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(AuthExceptionCase.TOKEN_INVALID);
            then(revokeRefreshTokenPort).shouldHaveNoInteractions();
        }
    }
}
