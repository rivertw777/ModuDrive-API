package com.moduDrive.auth.application.port.out;

import com.moduDrive.auth.domain.model.TokenPair.TokenFamilyId;

public interface IsFamilyRevokedPort {
    boolean isRevoked(TokenFamilyId familyId);
}
