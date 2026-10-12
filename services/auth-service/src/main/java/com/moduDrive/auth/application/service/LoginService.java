package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.LoginCommand;
import com.moduDrive.auth.application.port.in.usecase.LoginUseCase;
import com.moduDrive.auth.application.port.out.AuthenticateMemberPort;
import com.moduDrive.auth.application.port.out.CreateSessionPort;
import com.moduDrive.auth.application.port.out.DeleteSessionPort;
import com.moduDrive.auth.application.port.out.KnownDevicePort;
import com.moduDrive.auth.application.port.out.LoginAttemptPort;
import com.moduDrive.auth.application.port.out.LoginChallengePort;
import com.moduDrive.auth.domain.model.LoginResult;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.vo.DeviceId;
import com.moduDrive.auth.domain.vo.LoginChallengeId;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
class LoginService implements LoginUseCase {

    private final AuthenticateMemberPort authenticateMemberPort;
    private final CreateSessionPort createSessionPort;
    private final DeleteSessionPort deleteSessionPort;
    private final LoginAttemptPort loginAttemptPort;
    private final KnownDevicePort knownDevicePort;
    private final LoginChallengePort loginChallengePort;

    @Override
    public LoginResult login(LoginCommand loginCommand) {
        // Counted before the password check and cleared only once a session is issued, so knowing the
        // password still leaves few guesses at a new device's code. A device this email verified has
        // its own count, so failures elsewhere can't lock it out; a forged cookie lands on the shared
        // count (spec 004 2-2).
        DeviceId knownDevice = loginCommand.getDeviceId() != null
                && knownDevicePort.isKnown(loginCommand.getMemberEmail(), loginCommand.getDeviceId())
                ? loginCommand.getDeviceId() : null;
        if (!loginAttemptPort.tryAttempt(loginCommand.getMemberEmail(), knownDevice)) {
            throw new BusinessException(AuthExceptionCase.TOO_MANY_LOGIN_ATTEMPTS);
        }
        MemberAuthData memberAuthData = authenticateMemberPort.authenticateMember(
                loginCommand.getMemberEmail(), loginCommand.getMemberPassword());

        if (loginCommand.getDeviceId() != null
                && knownDevicePort.refreshIfKnown(
                        memberAuthData.getMemberId(), loginCommand.getMemberEmail(), loginCommand.getDeviceId())) {
            loginAttemptPort.clearAttempts(loginCommand.getMemberEmail(), knownDevice);
            // Always a new id (no session fixation); the browser's previous session is deleted.
            if (loginCommand.getPreviousSessionId() != null) {
                deleteSessionPort.deleteSession(loginCommand.getPreviousSessionId());
            }
            return new LoginResult.SignedIn(createSessionPort.createSession(memberAuthData), loginCommand.getDeviceId());
        }

        // A device this member hasn't verified: no session until the emailed code comes back, and no
        // mail until the member asks for the code (spec 004 2-1).
        LoginChallengeId challengeId =
                loginChallengePort.createChallenge(memberAuthData, loginCommand.getMemberEmail());
        return new LoginResult.VerificationRequired(challengeId);
    }

}
