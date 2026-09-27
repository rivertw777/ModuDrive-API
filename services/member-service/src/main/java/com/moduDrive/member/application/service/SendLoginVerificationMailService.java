package com.moduDrive.member.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.member.application.port.in.command.SendLoginVerificationMailCommand;
import com.moduDrive.member.application.port.in.usecase.SendLoginVerificationMailUseCase;
import com.moduDrive.member.application.port.out.FindMemberPort;
import com.moduDrive.member.application.port.out.PublishMailEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/** Mails a new-device login code (auth spec 004 2-1) to the member's own address — auth-service
 * made and holds the code; the address comes from here, never from the caller. */
@UseCase
@RequiredArgsConstructor
class SendLoginVerificationMailService implements SendLoginVerificationMailUseCase {

    private final FindMemberPort findMemberPort;
    private final PublishMailEventPort publishMailEventPort;

    @Transactional
    @Override
    public void sendLoginVerificationMail(SendLoginVerificationMailCommand command) {
        String email = findMemberPort.findMemberById(command.getMemberId()).getEmail();
        publishMailEventPort.publishLoginVerificationRequested(email, command.getCode());
    }
}
