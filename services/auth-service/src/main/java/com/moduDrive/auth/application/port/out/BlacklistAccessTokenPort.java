package com.moduDrive.auth.application.port.out;

import com.moduDrive.auth.domain.model.TokenPair.TokenJti;

import java.util.Date;

public interface BlacklistAccessTokenPort {
    void blacklist(TokenJti jti, Date expiresAt);
}
