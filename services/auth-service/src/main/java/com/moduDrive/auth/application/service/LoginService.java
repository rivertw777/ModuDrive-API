package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.LoginCommand;
import com.moduDrive.auth.application.port.in.usecase.LoginUseCase;
import com.moduDrive.auth.application.port.out.AuthenticateMemberPort;
import com.moduDrive.auth.application.port.out.CreateSessionPort;
import com.moduDrive.auth.application.port.out.DeleteSessionPort;
import com.moduDrive.auth.application.port.out.KnownDevicePort;
import com.moduDrive.auth.application.port.out.LoginAttemptPort;
import com.moduDrive.auth.application.port.out.LoginChallengePort;
import com.moduDrive.auth.application.port.out.SendLoginVerificationMailPort;
import com.moduDrive.auth.domain.model.LoginResult;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.vo.DeviceId;
import com.moduDrive.auth.domain.vo.LoginChallengeId;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.common.api.dto.member.AuthenticateMemberRequest;
import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.security.SecureRandom;

@UseCase
@RequiredArgsConstructor
class LoginService implements LoginUseCase {

    private final SecureRandom secureRandom = new SecureRandom();

    private final AuthenticateMemberPort authenticateMemberPort;
    private final CreateSessionPort createSessionPort;
    private final DeleteSessionPort deleteSessionPort;
    private final LoginAttemptPort loginAttemptPort;
    private final KnownDevicePort knownDevicePort;
    private final LoginChallengePort loginChallengePort;
    private final SendLoginVerificationMailPort sendLoginVerificationMailPort;

    @Override
    public LoginResult login(LoginCommand loginCommand) {
        // Counted before the password is even checked, and cleared only once a session is issued —
        // so someone who knows the password still gets few guesses at a new device's code. A device
        // this email verified counts on its own, so failures from elsewhere can't lock it out; a
        // made-up cookie isn't known and lands on the shared count (spec 004 2-1).
        DeviceId knownDevice = loginCommand.getDeviceId() != null
                && knownDevicePort.isKnown(loginCommand.getMemberEmail(), loginCommand.getDeviceId())
                ? loginCommand.getDeviceId() : null;
        if (!loginAttemptPort.tryAttempt(loginCommand.getMemberEmail(), knownDevice)) {
            throw new BusinessException(AuthExceptionCase.TOO_MANY_LOGIN_ATTEMPTS);
        }
        val request = new AuthenticateMemberRequest(
                loginCommand.getMemberEmail().value(),
                loginCommand.getMemberPassword().value()
        );
        MemberAuthData memberAuthData = authenticateMemberPort.authenticateMember(request);

        if (loginCommand.getDeviceId() != null
                && knownDevicePort.refreshIfKnown(
                        memberAuthData.getMemberId(), loginCommand.getMemberEmail(), loginCommand.getDeviceId())) {
            loginAttemptPort.clearAttempts(loginCommand.getMemberEmail(), knownDevice);
            // Always a brand-new id (no session fixation), and the one this browser held before is
            // dropped rather than left alive in Redis.
            if (loginCommand.getPreviousSessionId() != null) {
                deleteSessionPort.deleteSession(loginCommand.getPreviousSessionId());
            }
            return new LoginResult.SignedIn(createSessionPort.createSession(memberAuthData), loginCommand.getDeviceId());
        }

        // A device this member hasn't verified: no session until the emailed code comes back (spec 004 2-2).
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        LoginChallengeId challengeId =
                loginChallengePort.createChallenge(memberAuthData, loginCommand.getMemberEmail(), code);
        // The typed email is the member's own: member-service found them by it, exactly.
        sendLoginVerificationMailPort.sendLoginVerificationMail(loginCommand.getMemberEmail(), code);
        return new LoginResult.VerificationRequired(challengeId);
    }

}
