package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.ValidateTokenCommand;
import com.moduDrive.auth.application.port.in.usecase.ValidateTokenUseCase;
import com.moduDrive.auth.application.port.out.ValidateTokenPort;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.common.core.annotation.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@UseCase
class ValidateTokenService implements ValidateTokenUseCase {

    private final ValidateTokenPort validateTokenPort;

    @Override
    public MemberAuthData validateToken(ValidateTokenCommand validateTokenCommand) {
        return validateTokenPort.getMemberAuthDataFromToken(
                validateTokenCommand.getAccessToken()
        );
    }

}
