package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.ReissueTokenCommand;
import com.moduDrive.auth.application.port.out.FetchMemberStatusPort;
import com.moduDrive.auth.application.port.out.GenerateTokenPort;
import com.moduDrive.auth.application.port.out.RotateRefreshTokenPort;
import com.moduDrive.auth.application.port.out.ValidateTokenPort;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.model.RefreshTokenClaims;
import com.moduDrive.auth.domain.model.TokenPair;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class ReissueTokenServiceTest {

    @Mock
    private ValidateTokenPort validateTokenPort;
    @Mock
    private FetchMemberStatusPort fetchMemberStatusPort;
    @Mock
    private GenerateTokenPort generateTokenPort;
    @Mock
    private RotateRefreshTokenPort rotateRefreshTokenPort;
    @InjectMocks
    private ReissueTokenService reissueTokenService;

    private final ReissueTokenCommand command = new ReissueTokenCommand(TokenPairTestFixture.DEFAULT_REFRESH_TOKEN);

    private static final TokenJti PRESENTED_JTI = new TokenJti("presented-jti");
    private static final TokenJti NEW_JTI = new TokenJti("new-jti");

    private final RefreshTokenClaims claims = RefreshTokenClaims.create(
            MemberAuthDataTestFixture.aMemberAuthData(),
            TokenPairTestFixture.DEFAULT_FAMILY_ID,
            PRESENTED_JTI);

    private final MemberAuthData freshMemberAuthData =
            MemberAuthDataTestFixture.aMemberAuthDataWithRoles(List.of("ADMIN"));

    private final TokenPair newTokenPair = TokenPairTestFixture.aTokenPairWithJti(NEW_JTI);

    @Nested
    @DisplayName("제시된 리프레시 토큰이 해당 패밀리의 최신 토큰일 때")
    class WhenPresentedRefreshTokenIsCurrent {

        @Test
        void returnsTokenPairIssuedFromCurrentMemberStatus() {
            given(validateTokenPort.getRefreshTokenClaims(command.getRefreshToken())).willReturn(claims);
            given(fetchMemberStatusPort.fetchMemberStatus(MemberAuthDataTestFixture.DEFAULT_MEMBER_ID))
                    .willReturn(freshMemberAuthData);
            given(generateTokenPort.generateToken(freshMemberAuthData, claims.getFamilyId()))
                    .willReturn(newTokenPair);
            given(rotateRefreshTokenPort.rotateIfCurrent(
                    TokenPairTestFixture.DEFAULT_FAMILY_ID, PRESENTED_JTI, NEW_JTI))
                    .willReturn(true);

            TokenPair result = reissueTokenService.reissueToken(command);

            assertThat(result).isEqualTo(newTokenPair);
            then(generateTokenPort).should().generateToken(freshMemberAuthData, claims.getFamilyId());
        }
    }

    @Nested
    @DisplayName("이미 회전된 리프레시 토큰이 재사용되었을 때")
    class WhenPresentedRefreshTokenIsReused {

        @Test
        void throwsRefreshTokenReusedException() {
            given(validateTokenPort.getRefreshTokenClaims(command.getRefreshToken())).willReturn(claims);
            given(fetchMemberStatusPort.fetchMemberStatus(MemberAuthDataTestFixture.DEFAULT_MEMBER_ID))
                    .willReturn(freshMemberAuthData);
            given(generateTokenPort.generateToken(freshMemberAuthData, claims.getFamilyId()))
                    .willReturn(newTokenPair);
            given(rotateRefreshTokenPort.rotateIfCurrent(
                    TokenPairTestFixture.DEFAULT_FAMILY_ID, PRESENTED_JTI, NEW_JTI))
                    .willReturn(false);

            Throwable thrown = catchThrowable(() -> reissueTokenService.reissueToken(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(AuthExceptionCase.REFRESH_TOKEN_REUSED);
        }
    }

    @Nested
    @DisplayName("회원이 더 이상 유효하지 않을 때")
    class WhenMemberIsNoLongerValid {

        @Test
        void throwsMemberNotValidExceptionWithoutIssuingToken() {
            given(validateTokenPort.getRefreshTokenClaims(command.getRefreshToken())).willReturn(claims);
            willThrow(new BusinessException(AuthExceptionCase.MEMBER_NOT_VALID))
                    .given(fetchMemberStatusPort).fetchMemberStatus(MemberAuthDataTestFixture.DEFAULT_MEMBER_ID);

            Throwable thrown = catchThrowable(() -> reissueTokenService.reissueToken(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(AuthExceptionCase.MEMBER_NOT_VALID);
            then(generateTokenPort).shouldHaveNoInteractions();
            then(rotateRefreshTokenPort).shouldHaveNoInteractions();
        }
    }
}
