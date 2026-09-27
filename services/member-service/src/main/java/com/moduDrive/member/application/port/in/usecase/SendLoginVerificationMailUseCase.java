package com.moduDrive.member.application.port.in.usecase;

import com.moduDrive.member.application.port.in.command.SendLoginVerificationMailCommand;

public interface SendLoginVerificationMailUseCase {
    void sendLoginVerificationMail(SendLoginVerificationMailCommand command);
}
