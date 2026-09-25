package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.LogoutCommand;
import com.moduDrive.auth.application.port.in.usecase.LogoutUseCase;
import com.moduDrive.auth.application.port.out.RevokeRefreshTokenPort;
import com.moduDrive.auth.application.port.out.ValidateTokenPort;
import com.moduDrive.auth.domain.model.RefreshTokenClaims;
import com.moduDrive.common.core.annotation.UseCase;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
class LogoutService implements LogoutUseCase {

    private final ValidateTokenPort validateTokenPort;
    private final RevokeRefreshTokenPort revokeRefreshTokenPort;

    @Override
    public void logout(LogoutCommand logoutCommand) {
        RefreshTokenClaims refreshClaims = validateTokenPort.getRefreshTokenClaims(
                logoutCommand.getRefreshToken()
        );
        // Revoking the family also rejects every access token it issued (see ValidateTokenService)
        revokeRefreshTokenPort.revoke(refreshClaims.getFamilyId());
    }

}
