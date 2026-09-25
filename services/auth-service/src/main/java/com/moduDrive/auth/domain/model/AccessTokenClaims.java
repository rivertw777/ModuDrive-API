package com.moduDrive.auth.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AccessTokenClaims {

    private final MemberAuthData memberAuthData;
    private final TokenPair.TokenFamilyId familyId;

    public static AccessTokenClaims create(MemberAuthData memberAuthData,
                                           TokenPair.TokenFamilyId familyId) {
        return new AccessTokenClaims(memberAuthData, familyId);
    }

}
