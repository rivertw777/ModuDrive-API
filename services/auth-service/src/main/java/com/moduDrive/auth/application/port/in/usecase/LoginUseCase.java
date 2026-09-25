package com.moduDrive.auth.application.port.in.usecase;

import com.moduDrive.auth.application.port.in.command.LoginCommand;
import com.moduDrive.auth.domain.vo.SessionId;

public interface LoginUseCase {
    SessionId login(LoginCommand loginCommand);
}
