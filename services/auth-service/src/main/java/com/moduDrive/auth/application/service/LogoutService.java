package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.LogoutCommand;
import com.moduDrive.auth.application.port.in.usecase.LogoutUseCase;
import com.moduDrive.auth.application.port.out.DeleteSessionPort;
import com.moduDrive.common.core.annotation.UseCase;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
class LogoutService implements LogoutUseCase {

    private final DeleteSessionPort deleteSessionPort;

    @Override
    public void logout(LogoutCommand logoutCommand) {
        deleteSessionPort.deleteSession(logoutCommand.getSessionId());
    }

}
