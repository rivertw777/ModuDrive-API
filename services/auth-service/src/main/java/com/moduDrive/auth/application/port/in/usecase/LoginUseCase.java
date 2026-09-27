package com.moduDrive.auth.application.port.in.usecase;

import com.moduDrive.auth.application.port.in.command.LoginCommand;
import com.moduDrive.auth.domain.model.LoginResult;

public interface LoginUseCase {
    LoginResult login(LoginCommand loginCommand);
}
