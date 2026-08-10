package com.moduDrive.auth.domain.model;

import lombok.*;

import java.util.Date;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TokenPair {

    private final String accessToken;
    private final String refreshToken;
    private final String grantType;
    private Date issuedAt;
    private final String familyId;
    private final String jti;

    public static TokenPair create(AccessToken accessToken,
                                   RefreshToken refreshToken,
                                   TokenGrantType grantType,
                                   TokenIssuedAt tokenIssuedAt,
                                   TokenFamilyId familyId,
                                   TokenJti jti) {
        return new TokenPair(
                accessToken.tokenValue,
                refreshToken.tokenValue,
                grantType.grantTypeValue,
                tokenIssuedAt.issuedAtValue,
                familyId.familyIdValue,
                jti.jtiValue
        );
    }

    @Value
    public static class AccessToken {
        public AccessToken(String value) {
            this.tokenValue = value;
        }
        String tokenValue;
    }

    @Value
    public static class RefreshToken {
        public RefreshToken(String value) {
            this.tokenValue = value;
        }
        String tokenValue;
    }

    @Value
    public static class TokenGrantType {
        public TokenGrantType(String value) {
            this.grantTypeValue = value;
        }
        String grantTypeValue;
    }

    @Value
    public static class TokenIssuedAt {
        public TokenIssuedAt(Date value) {
            this.issuedAtValue = value;
        }
        Date issuedAtValue;
    }

    @Value
    public static class TokenFamilyId {
        public TokenFamilyId(String value) {
            this.familyIdValue = value;
        }
        String familyIdValue;
    }

    @Value
    public static class TokenJti {
        public TokenJti(String value) {
            this.jtiValue = value;
        }
        String jtiValue;
    }

}