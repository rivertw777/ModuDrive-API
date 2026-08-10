package com.moduDrive.auth.application.port.out;

import com.moduDrive.auth.domain.model.TokenPair.TokenFamilyId;

public interface RevokeRefreshTokenPort {
    void revoke(TokenFamilyId familyId);
}
