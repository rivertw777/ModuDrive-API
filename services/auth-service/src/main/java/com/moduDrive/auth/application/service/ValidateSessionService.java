package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.ValidateSessionCommand;
import com.moduDrive.auth.application.port.in.usecase.ValidateSessionUseCase;
import com.moduDrive.auth.application.port.out.FindSessionPort;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
class ValidateSessionService implements ValidateSessionUseCase {

    private final FindSessionPort findSessionPort;

    @Override
    public MemberAuthData validateSession(ValidateSessionCommand validateSessionCommand) {
        return findSessionPort.findSession(validateSessionCommand.getSessionId(), validateSessionCommand.isTouch())
                .orElseThrow(() -> new BusinessException(AuthExceptionCase.SESSION_NOT_FOUND));
    }

}
