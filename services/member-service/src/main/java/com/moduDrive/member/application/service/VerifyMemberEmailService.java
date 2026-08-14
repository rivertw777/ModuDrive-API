package com.moduDrive.member.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.member.application.port.in.command.VerifyMemberEmailCommand;
import com.moduDrive.member.application.port.in.usecase.VerifyMemberEmailUseCase;
import com.moduDrive.member.application.port.out.EmailVerificationTokenPort;
import com.moduDrive.member.application.port.out.UpdateMemberValidityPort;
import com.moduDrive.member.domain.model.Member.MemberId;
import com.moduDrive.member.exception.MemberExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
class VerifyMemberEmailService implements VerifyMemberEmailUseCase {

    private final EmailVerificationTokenPort emailVerificationTokenPort;
    private final UpdateMemberValidityPort updateMemberValidityPort;

    @Transactional
    @Override
    public void verifyMemberEmail(VerifyMemberEmailCommand command) {
        var memberId = emailVerificationTokenPort.consumeToken(command.getToken())
                .orElseThrow(() -> new BusinessException(MemberExceptionCase.INVALID_VERIFICATION_TOKEN));

        updateMemberValidityPort.markEmailVerified(new MemberId(memberId));
    }
}
