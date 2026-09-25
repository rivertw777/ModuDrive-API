package com.moduDrive.auth.application.port.in.usecase;

import com.moduDrive.auth.application.port.in.command.ValidateSessionCommand;
import com.moduDrive.auth.domain.model.MemberAuthData;

public interface ValidateSessionUseCase {
    MemberAuthData validateSession(ValidateSessionCommand validateSessionCommand);
}
