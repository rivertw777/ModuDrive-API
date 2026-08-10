package com.moduDrive.auth.application.port.out;

import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.model.TokenPair;

public interface GenerateTokenPort {
    TokenPair generateToken(MemberAuthData memberAuthData);
    TokenPair generateToken(MemberAuthData memberAuthData, TokenPair.TokenFamilyId familyId);
}
