package com.moduDrive.member.application.port.in.usecase;

import com.moduDrive.member.application.port.in.command.ChangePasswordCommand;

public interface ChangePasswordUseCase {
    void changePassword(ChangePasswordCommand changePasswordCommand);
}
