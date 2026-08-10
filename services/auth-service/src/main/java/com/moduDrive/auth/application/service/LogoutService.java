package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.LogoutCommand;
import com.moduDrive.auth.application.port.in.usecase.LogoutUseCase;
import com.moduDrive.auth.application.port.out.BlacklistAccessTokenPort;
import com.moduDrive.auth.application.port.out.RevokeRefreshTokenPort;
import com.moduDrive.auth.application.port.out.ValidateTokenPort;
import com.moduDrive.auth.domain.model.AccessTokenClaims;
import com.moduDrive.auth.domain.model.RefreshTokenClaims;
import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
class LogoutService implements LogoutUseCase {

    private final ValidateTokenPort validateTokenPort;
    private final RevokeRefreshTokenPort revokeRefreshTokenPort;
    private final BlacklistAccessTokenPort blacklistAccessTokenPort;

    @Override
    public void logout(LogoutCommand logoutCommand) {
        RefreshTokenClaims refreshClaims = validateTokenPort.getRefreshTokenClaims(
                logoutCommand.getRefreshToken()
        );
        revokeRefreshTokenPort.revoke(refreshClaims.getFamilyId());

        if (logoutCommand.getAccessToken() != null) {
            try {
                AccessTokenClaims accessClaims = validateTokenPort.getAccessTokenClaims(
                        logoutCommand.getAccessToken()
                );
                blacklistAccessTokenPort.blacklist(accessClaims.getJti(), accessClaims.getExpiresAt());
            } catch (BusinessException e) {
                // Access token is already invalid/expired on its own — nothing left to blacklist.
                // Logout still succeeds: the refresh-token family was revoked above.
            }
        }
    }

}
