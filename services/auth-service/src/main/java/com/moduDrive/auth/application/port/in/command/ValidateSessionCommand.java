package com.moduDrive.auth.application.port.in.command;

import com.moduDrive.auth.domain.vo.SessionId;
import com.moduDrive.common.core.validation.SelfValidating;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class ValidateSessionCommand extends SelfValidating<ValidateSessionCommand> {

    @NotNull
    private final SessionId sessionId;

    // false for background requests (polling), so they can't keep an idle session alive.
    private final boolean touch;

    public ValidateSessionCommand(SessionId sessionId, boolean touch) {
        this.sessionId = sessionId;
        this.touch = touch;
        this.validateSelf();
    }
}
