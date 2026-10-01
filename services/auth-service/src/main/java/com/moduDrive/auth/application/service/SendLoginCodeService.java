package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.usecase.SendLoginCodeUseCase;
import com.moduDrive.auth.application.port.out.LoginChallengePort;
import com.moduDrive.auth.application.port.out.SendLoginVerificationMailPort;
import com.moduDrive.auth.domain.vo.LoginChallengeId;
import com.moduDrive.auth.domain.vo.MemberEmail;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;

import java.security.SecureRandom;

/** Mails a new-device login its code, first time or again — each send a fresh code (spec 004 2-2). */
@UseCase
@RequiredArgsConstructor
class SendLoginCodeService implements SendLoginCodeUseCase {

    private final SecureRandom secureRandom = new SecureRandom();

    private final LoginChallengePort loginChallengePort;
    private final SendLoginVerificationMailPort sendLoginVerificationMailPort;

    @Override
    public void sendLoginCode(LoginChallengeId challengeId) {
        MemberEmail memberEmail = loginChallengePort.findEmail(challengeId);
        // Same limits as sign-up's code requests: a 30s gap, and 5 per address per 15 minutes — which
        // also caps the guesses at 25 codes' worth.
        switch (loginChallengePort.requestCode(memberEmail)) {
            case TOO_SOON -> throw new BusinessException(AuthExceptionCase.LOGIN_CODE_REQUEST_TOO_SOON);
            case TOO_MANY -> throw new BusinessException(AuthExceptionCase.TOO_MANY_LOGIN_CODE_REQUESTS);
            case ALLOWED -> { }
        }
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        loginChallengePort.issueCode(challengeId, code);
        // The email is the member's own: member-service found them by it, exactly.
        sendLoginVerificationMailPort.sendLoginVerificationMail(memberEmail, code);
    }
}
