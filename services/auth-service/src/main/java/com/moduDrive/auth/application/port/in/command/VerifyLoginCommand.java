package com.moduDrive.auth.application.port.in.command;

import com.moduDrive.auth.domain.vo.DeviceId;
import com.moduDrive.auth.domain.vo.LoginChallengeId;
import com.moduDrive.auth.domain.vo.SessionId;
import com.moduDrive.common.core.validation.SelfValidating;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class VerifyLoginCommand extends SelfValidating<VerifyLoginCommand> {

    @NotNull
    private final LoginChallengeId challengeId;

    @NotBlank
    private final String code;

    // Device cookie, if any — kept as the device's id so one browser stays one device across members.
    private final DeviceId deviceId;

    // Session cookie the browser already carried, if any — dropped once the new session exists.
    private final SessionId previousSessionId;

    public VerifyLoginCommand(LoginChallengeId challengeId, String code, DeviceId deviceId, SessionId previousSessionId) {
        this.challengeId = challengeId;
        this.code = code;
        this.deviceId = deviceId;
        this.previousSessionId = previousSessionId;
        this.validateSelf();
    }
}
