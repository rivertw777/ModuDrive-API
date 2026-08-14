package com.moduDrive.member.application.port.in.usecase;

import com.moduDrive.member.application.port.in.command.ConfirmEmailVerificationCommand;

public interface ConfirmEmailVerificationUseCase {
    void confirmEmailVerification(ConfirmEmailVerificationCommand command);
}
