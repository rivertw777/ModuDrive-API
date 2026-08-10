package com.moduDrive.auth.application.port.in.command;

import com.moduDrive.auth.domain.model.TokenPair.RefreshToken;
import com.moduDrive.common.core.validation.SelfValidating;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class ReissueTokenCommand extends SelfValidating<ReissueTokenCommand> {

    @NotNull
    private final RefreshToken refreshToken;

    public ReissueTokenCommand(RefreshToken refreshToken) {
        this.refreshToken = refreshToken;
        this.validateSelf();
    }
}
