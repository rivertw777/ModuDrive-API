package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.ValidateTokenCommand;
import com.moduDrive.auth.application.port.in.usecase.ValidateTokenUseCase;
import com.moduDrive.auth.application.port.out.IsAccessTokenBlacklistedPort;
import com.moduDrive.auth.application.port.out.IsFamilyRevokedPort;
import com.moduDrive.auth.application.port.out.ValidateTokenPort;
import com.moduDrive.auth.domain.model.AccessTokenClaims;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@UseCase
class ValidateTokenService implements ValidateTokenUseCase {

    private final ValidateTokenPort validateTokenPort;
    private final IsAccessTokenBlacklistedPort isAccessTokenBlacklistedPort;
    private final IsFamilyRevokedPort isFamilyRevokedPort;

    @Override
    public MemberAuthData validateToken(ValidateTokenCommand validateTokenCommand) {
        AccessTokenClaims claims = validateTokenPort.getAccessTokenClaims(
                validateTokenCommand.getAccessToken()
        );

        if (isAccessTokenBlacklistedPort.isBlacklisted(claims.getJti())
                || isFamilyRevokedPort.isRevoked(claims.getFamilyId())) {
            throw new BusinessException(AuthExceptionCase.ACCESS_TOKEN_REVOKED);
        }

        return claims.getMemberAuthData();
    }

}
