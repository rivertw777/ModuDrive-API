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

import java.security.SecureRandom;

@UseCase
@RequiredArgsConstructor
class RequestEmailVerificationService implements RequestEmailVerificationUseCase {

    private final SecureRandom secureRandom = new SecureRandom();

    private final CheckEmailExistsPort checkEmailExistsPort;
    private final EmailVerificationTokenPort emailVerificationTokenPort;
    private final PublishMailEventPort publishMailEventPort;

    @Override
    public void requestEmailVerification(RequestEmailVerificationCommand command) {
        String email = command.getMemberEmail().emailValue();
        if (checkEmailExistsPort.existsByEmail(command.getMemberEmail())) {
            throw new BusinessException(MemberExceptionCase.DUPLICATE_EMAIL);
        }

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        emailVerificationTokenPort.saveCode(email, code);
        publishMailEventPort.publishVerificationRequested(email, code);
    }
}
