package com.moduDrive.member.application.port.in.usecase;

import com.moduDrive.member.application.port.in.command.RequestEmailVerificationCommand;

public interface RequestEmailVerificationUseCase {
    void requestEmailVerification(RequestEmailVerificationCommand command);
}
