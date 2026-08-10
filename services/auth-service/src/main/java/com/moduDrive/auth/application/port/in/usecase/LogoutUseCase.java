package com.moduDrive.auth.application.port.in.usecase;

import com.moduDrive.auth.application.port.in.command.LogoutCommand;

public interface LogoutUseCase {
    void logout(LogoutCommand logoutCommand);
}
