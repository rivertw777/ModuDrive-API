package com.moduDrive.member.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.member.application.port.in.command.ConfirmEmailVerificationCommand;
import com.moduDrive.member.application.port.in.usecase.ConfirmEmailVerificationUseCase;
import com.moduDrive.member.application.port.out.EmailVerificationTokenPort;
import com.moduDrive.member.exception.MemberExceptionCase;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
class ConfirmEmailVerificationService implements ConfirmEmailVerificationUseCase {

    private final EmailVerificationTokenPort emailVerificationTokenPort;

    @Override
    public void confirmEmailVerification(ConfirmEmailVerificationCommand command) {
        String email = command.getMemberEmail().emailValue();
        if (!emailVerificationTokenPort.confirmCode(email, command.getCode())) {
            throw new BusinessException(MemberExceptionCase.INVALID_VERIFICATION_CODE);
        }

        emailVerificationTokenPort.markVerified(email);
    }
}
