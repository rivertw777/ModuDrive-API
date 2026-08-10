package com.moduDrive.auth.application.port.out;

import com.moduDrive.auth.domain.model.AccessTokenClaims;
import com.moduDrive.auth.domain.model.RefreshTokenClaims;
import com.moduDrive.auth.domain.model.TokenPair.*;

public interface ValidateTokenPort {
    AccessTokenClaims getAccessTokenClaims(AccessToken accessToken);
    RefreshTokenClaims getRefreshTokenClaims(RefreshToken refreshToken);
}
