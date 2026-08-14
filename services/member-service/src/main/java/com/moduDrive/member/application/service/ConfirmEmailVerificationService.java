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
        String email = emailVerificationTokenPort.consumeToken(command.getToken())
                .orElseThrow(() -> new BusinessException(MemberExceptionCase.INVALID_VERIFICATION_TOKEN));

        emailVerificationTokenPort.markVerified(email);
    }
}
