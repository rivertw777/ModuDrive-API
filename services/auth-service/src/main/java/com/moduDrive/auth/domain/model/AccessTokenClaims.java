package com.moduDrive.auth.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Date;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AccessTokenClaims {

    private final MemberAuthData memberAuthData;
    private final TokenPair.TokenJti jti;
    private final TokenPair.TokenFamilyId familyId;
    private final Date expiresAt;

    public static AccessTokenClaims create(MemberAuthData memberAuthData,
                                           TokenPair.TokenJti jti,
                                           TokenPair.TokenFamilyId familyId,
                                           Date expiresAt) {
        return new AccessTokenClaims(memberAuthData, jti, familyId, expiresAt);
    }

}
