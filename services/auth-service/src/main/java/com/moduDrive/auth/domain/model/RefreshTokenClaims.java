package com.moduDrive.auth.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RefreshTokenClaims {

    private final MemberAuthData memberAuthData;
    private final TokenPair.TokenFamilyId familyId;
    private final TokenPair.TokenJti jti;

    public static RefreshTokenClaims create(MemberAuthData memberAuthData,
                                            TokenPair.TokenFamilyId familyId,
                                            TokenPair.TokenJti jti) {
        return new RefreshTokenClaims(memberAuthData, familyId, jti);
    }

}
