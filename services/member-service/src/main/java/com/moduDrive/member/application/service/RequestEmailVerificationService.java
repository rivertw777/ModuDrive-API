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
        if (!emailVerificationTokenPort.tryRequestCode(email)) {
            throw new BusinessException(MemberExceptionCase.TOO_MANY_VERIFICATION_REQUESTS);
        }
        // Same answer as a new address, so the response can't tell whether an email is registered.
        // A registered address just never gets a code, and sign-up can't pass without one.
        if (checkEmailExistsPort.existsByEmail(command.getMemberEmail())) {
            return;
        }

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        emailVerificationTokenPort.saveCode(email, code);
        publishMailEventPort.publishVerificationRequested(email, code);
    }
}
