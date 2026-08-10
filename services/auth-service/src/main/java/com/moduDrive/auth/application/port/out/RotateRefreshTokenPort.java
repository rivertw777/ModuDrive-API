package com.moduDrive.auth.application.port.out;

import com.moduDrive.auth.domain.model.TokenPair.TokenFamilyId;
import com.moduDrive.auth.domain.model.TokenPair.TokenJti;

public interface RotateRefreshTokenPort {
    boolean rotateIfCurrent(TokenFamilyId familyId, TokenJti presentedJti, TokenJti newJti);
}
