package com.moduDrive.auth.application.port.in.command;

import com.moduDrive.auth.domain.vo.SessionId;
import com.moduDrive.common.core.validation.SelfValidating;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class LogoutCommand extends SelfValidating<LogoutCommand> {

    @NotNull
    private final SessionId sessionId;

    public LogoutCommand(SessionId sessionId) {
        this.sessionId = sessionId;
        this.validateSelf();
    }
}
