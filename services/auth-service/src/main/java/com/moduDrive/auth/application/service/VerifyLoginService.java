package com.moduDrive.auth.application.service;

import com.moduDrive.auth.application.port.in.command.VerifyLoginCommand;
import com.moduDrive.auth.application.port.in.usecase.VerifyLoginUseCase;
import com.moduDrive.auth.application.port.out.CreateSessionPort;
import com.moduDrive.auth.application.port.out.DeleteSessionPort;
import com.moduDrive.auth.application.port.out.KnownDevicePort;
import com.moduDrive.auth.application.port.out.LoginAttemptPort;
import com.moduDrive.auth.application.port.out.LoginChallengePort;
import com.moduDrive.auth.domain.model.LoginChallenge;
import com.moduDrive.auth.domain.model.LoginResult;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.vo.DeviceId;
import com.moduDrive.common.core.annotation.UseCase;
import lombok.RequiredArgsConstructor;

/** The second step of a login from a new device: the emailed code (spec 004 2-1). */
@UseCase
@RequiredArgsConstructor
class VerifyLoginService implements VerifyLoginUseCase {

    private final LoginChallengePort loginChallengePort;
    private final KnownDevicePort knownDevicePort;
    private final LoginAttemptPort loginAttemptPort;
    private final DeleteSessionPort deleteSessionPort;
    private final CreateSessionPort createSessionPort;

    @Override
    public LoginResult.SignedIn verifyLogin(VerifyLoginCommand command) {
        LoginChallenge challenge = loginChallengePort.confirmChallenge(command.getChallengeId(), command.getCode());
        MemberAuthData memberAuthData = challenge.memberAuthData();

        DeviceId deviceId = knownDevicePort.remember(memberAuthData.getMemberId(), command.getDeviceId());
        loginAttemptPort.clearAttempts(challenge.memberEmail());
        if (command.getPreviousSessionId() != null) {
            deleteSessionPort.deleteSession(command.getPreviousSessionId());
        }
        return new LoginResult.SignedIn(createSessionPort.createSession(memberAuthData), deviceId);
    }
}
