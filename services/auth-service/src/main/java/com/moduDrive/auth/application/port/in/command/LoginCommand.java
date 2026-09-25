package com.moduDrive.auth.application.port.in.command;

import com.moduDrive.auth.domain.vo.MemberEmail;
import com.moduDrive.auth.domain.vo.MemberPassword;
import com.moduDrive.auth.domain.vo.SessionId;
import com.moduDrive.common.core.validation.SelfValidating;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class LoginCommand extends SelfValidating<LoginCommand> {

    @NotNull
    private final MemberEmail memberEmail;

    @NotNull
    private final MemberPassword memberPassword;

    // Session cookie the browser already carried, if any — dropped once the new session exists.
    private final SessionId previousSessionId;

    public LoginCommand(MemberEmail memberEmail, MemberPassword memberPassword, SessionId previousSessionId) {
        this.memberEmail = memberEmail;
        this.memberPassword = memberPassword;
        this.previousSessionId = previousSessionId;
        this.validateSelf();
    }
}
