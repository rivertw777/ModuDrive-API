package com.moduDrive.notification.application.port.in.usecase;

import com.moduDrive.notification.application.port.in.command.SendVerificationMailCommand;

public interface SendVerificationMailUseCase {
    void sendVerificationMail(SendVerificationMailCommand command);
}
