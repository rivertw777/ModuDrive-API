package com.moduDrive.mail.application.port.in.usecase;

import com.moduDrive.mail.application.port.in.command.SendVerificationMailCommand;

public interface SendVerificationMailUseCase {
    void sendVerificationMail(SendVerificationMailCommand command);
}
