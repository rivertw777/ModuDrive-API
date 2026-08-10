package com.moduDrive.auth.application.port.in.command;

import com.moduDrive.auth.domain.model.TokenPair.AccessToken;
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

    // Optional: the client may log out with an already-expired or absent access token.
    private final AccessToken accessToken;

    public LogoutCommand(RefreshToken refreshToken, AccessToken accessToken) {
        this.refreshToken = refreshToken;
        this.accessToken = accessToken;
        this.validateSelf();
    }
}
