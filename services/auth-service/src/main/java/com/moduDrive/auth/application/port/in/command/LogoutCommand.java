package com.moduDrive.auth.application.port.in.command;

import com.moduDrive.auth.domain.model.TokenPair.RefreshToken;
import com.moduDrive.common.core.validation.SelfValidating;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class LogoutCommand extends SelfValidating<LogoutCommand> {

    @NotNull
    private final RefreshToken refreshToken;

    public LogoutCommand(RefreshToken refreshToken) {
        this.refreshToken = refreshToken;
        this.validateSelf();
    }
}
