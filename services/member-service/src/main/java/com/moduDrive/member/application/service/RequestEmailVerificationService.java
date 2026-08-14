package com.moduDrive.member.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.member.application.port.in.command.RequestEmailVerificationCommand;
import com.moduDrive.member.application.port.in.usecase.RequestEmailVerificationUseCase;
import com.moduDrive.member.application.port.out.CheckEmailExistsPort;
import com.moduDrive.member.application.port.out.EmailVerificationTokenPort;
import com.moduDrive.member.application.port.out.PublishMailEventPort;
import com.moduDrive.member.exception.MemberExceptionCase;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@UseCase
@RequiredArgsConstructor
class RequestEmailVerificationService implements RequestEmailVerificationUseCase {

    private final CheckEmailExistsPort checkEmailExistsPort;
    private final EmailVerificationTokenPort emailVerificationTokenPort;
    private final PublishMailEventPort publishMailEventPort;

    @Override
    public void requestEmailVerification(RequestEmailVerificationCommand command) {
        String email = command.getMemberEmail().emailValue();
        if (checkEmailExistsPort.existsByEmail(command.getMemberEmail())) {
            throw new BusinessException(MemberExceptionCase.DUPLICATE_EMAIL);
        }

        String token = UUID.randomUUID().toString();
        emailVerificationTokenPort.saveToken(token, email);
        publishMailEventPort.publishVerificationRequested(email, token);
    }
}
