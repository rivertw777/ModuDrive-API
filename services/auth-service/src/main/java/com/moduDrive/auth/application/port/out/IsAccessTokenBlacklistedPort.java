package com.moduDrive.auth.application.port.out;

import com.moduDrive.auth.domain.model.TokenPair.TokenJti;

public interface IsAccessTokenBlacklistedPort {
    boolean isBlacklisted(TokenJti jti);
}
