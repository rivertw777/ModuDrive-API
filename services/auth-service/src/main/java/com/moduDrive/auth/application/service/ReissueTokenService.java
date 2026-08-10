package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.ReissueTokenCommand;
import com.moduDrive.auth.application.port.in.usecase.ReissueTokenUseCase;
import com.moduDrive.auth.application.port.out.FetchMemberStatusPort;
import com.moduDrive.auth.application.port.out.GenerateTokenPort;
import com.moduDrive.auth.application.port.out.RotateRefreshTokenPort;
import com.moduDrive.auth.application.port.out.ValidateTokenPort;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.model.MemberAuthData.MemberId;
import com.moduDrive.auth.domain.model.RefreshTokenClaims;
import com.moduDrive.auth.domain.model.TokenPair;
import com.moduDrive.auth.domain.model.TokenPair.TokenJti;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
class ReissueTokenService implements ReissueTokenUseCase {

    private final ValidateTokenPort validateTokenPort;
    private final FetchMemberStatusPort fetchMemberStatusPort;
    private final GenerateTokenPort generateTokenPort;
    private final RotateRefreshTokenPort rotateRefreshTokenPort;

    @Override
    public TokenPair reissueToken(ReissueTokenCommand reissueTokenCommand) {
        RefreshTokenClaims claims = validateTokenPort.getRefreshTokenClaims(
                reissueTokenCommand.getRefreshToken()
        );

        MemberAuthData memberAuthData = fetchMemberStatusPort.fetchMemberStatus(
                new MemberId(claims.getMemberAuthData().getMemberId())
        );

        TokenPair tokenPair = generateTokenPort.generateToken(
                memberAuthData,
                claims.getFamilyId()
        );

        boolean rotated = rotateRefreshTokenPort.rotateIfCurrent(
                claims.getFamilyId(),
                claims.getJti(),
                new TokenJti(tokenPair.getJti())
        );
        if (!rotated) {
            throw new BusinessException(AuthExceptionCase.REFRESH_TOKEN_REUSED);
        }

        return tokenPair;
    }

}
