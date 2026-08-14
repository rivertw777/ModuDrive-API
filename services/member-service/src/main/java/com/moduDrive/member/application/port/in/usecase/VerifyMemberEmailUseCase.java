package com.moduDrive.member.application.port.in.usecase;

import com.moduDrive.member.application.port.in.command.VerifyMemberEmailCommand;

public interface VerifyMemberEmailUseCase {
    void verifyMemberEmail(VerifyMemberEmailCommand command);
}
