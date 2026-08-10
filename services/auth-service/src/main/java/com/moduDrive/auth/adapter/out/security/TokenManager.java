package com.moduDrive.auth.adapter.out.security;

import com.moduDrive.auth.application.port.out.GenerateTokenPort;
import com.moduDrive.auth.application.port.out.ValidateTokenPort;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.model.MemberAuthData.MemberId;
import com.moduDrive.auth.domain.model.MemberAuthData.MemberRoles;
import com.moduDrive.auth.domain.model.AccessTokenClaims;
import com.moduDrive.auth.domain.model.RefreshTokenClaims;
import com.moduDrive.auth.domain.model.TokenPair;
import com.moduDrive.auth.domain.model.TokenPair.AccessToken;
import com.moduDrive.auth.domain.model.TokenPair.RefreshToken;
import com.moduDrive.auth.domain.model.TokenPair.TokenFamilyId;
import com.moduDrive.auth.domain.model.TokenPair.TokenGrantType;
import com.moduDrive.auth.domain.model.TokenPair.TokenIssuedAt;
import com.moduDrive.auth.domain.model.TokenPair.TokenJti;
import com.moduDrive.common.core.exception.BusinessException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
class TokenManager implements GenerateTokenPort, ValidateTokenPort {

    private final Key secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public TokenManager(@Value("${jwt.secret}") String secret,
                        @Value("${jwt.accessToken.expiration}") long accessTokenExpiration,
                        @Value("${jwt.refreshToken.expiration}") long refreshTokenExpiration) {
        byte[] bytes = Base64.getDecoder().decode(secret);
        this.secretKey = Keys.hmacShaKeyFor(bytes);
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    @Override
    public TokenPair generateToken(MemberAuthData memberAuthData) {
        return generateToken(memberAuthData, new TokenFamilyId(UUID.randomUUID().toString()));
    }

    @Override
    public TokenPair generateToken(MemberAuthData memberAuthData, TokenFamilyId familyId) {
        Date now = new Date();
        String jti = UUID.randomUUID().toString();
        String accessToken = createAccessToken(
                memberAuthData.getMemberId(),
                memberAuthData.getMemberRoles(),
                now,
                familyId.getFamilyIdValue(),
                UUID.randomUUID().toString()
        );
        String refreshToken = createRefreshToken(
                memberAuthData.getMemberId(),
                memberAuthData.getMemberRoles(),
                now,
                familyId.getFamilyIdValue(),
                jti
        );

        return TokenPair.create(
                new AccessToken(accessToken),
                new RefreshToken(refreshToken),
                new TokenGrantType("Bearer"),
                new TokenIssuedAt(now),
                familyId,
                new TokenJti(jti)
        );
    }

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final String CLAIM_FAMILY_ID = "fid";

    private String createAccessToken(String memberId, List<String> memberRoles, Date issuedAt,
                                     String familyId, String jti) {
        return Jwts.builder()
                .setSubject(memberId)
                .setId(jti)
                .claim("roles", String.join(",", memberRoles))
                .claim("type", TYPE_ACCESS)
                .claim(CLAIM_FAMILY_ID, familyId)
                .setIssuedAt(issuedAt)
                .setExpiration(new Date(issuedAt.getTime() + accessTokenExpiration))
                .signWith(this.secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    private String createRefreshToken(String memberId, List<String> memberRoles, Date issuedAt,
                                      String familyId, String jti) {
        return Jwts.builder()
                .setSubject(memberId)
                .setId(jti)
                .claim("roles", String.join(",", memberRoles))
                .claim("type", TYPE_REFRESH)
                .claim(CLAIM_FAMILY_ID, familyId)
                .setIssuedAt(issuedAt)
                .setExpiration(new Date(issuedAt.getTime() + refreshTokenExpiration))
                .signWith(this.secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    @Override
    public AccessTokenClaims getAccessTokenClaims(AccessToken accessToken) {
        Claims claims = parseClaimsOfType(accessToken.getTokenValue(), TYPE_ACCESS);

        return AccessTokenClaims.create(
                toMemberAuthData(claims),
                new TokenJti(claims.getId()),
                new TokenFamilyId(claims.get(CLAIM_FAMILY_ID, String.class)),
                claims.getExpiration()
        );
    }

    @Override
    public RefreshTokenClaims getRefreshTokenClaims(RefreshToken refreshToken) {
        Claims claims = parseClaimsOfType(refreshToken.getTokenValue(), TYPE_REFRESH);

        return RefreshTokenClaims.create(
                toMemberAuthData(claims),
                new TokenFamilyId(claims.get(CLAIM_FAMILY_ID, String.class)),
                new TokenJti(claims.getId())
        );
    }

    private MemberAuthData toMemberAuthData(Claims claims) {
        String memberId = claims.getSubject();
        String rolesString = claims.get("roles", String.class);
        List<String> memberRoles = Arrays.asList(rolesString.split(","));

        return MemberAuthData.create(
                new MemberId(memberId),
                new MemberRoles(memberRoles)
        );
    }

    private Claims parseClaimsOfType(String token, String expectedType) {
        Claims claims = parseClaims(token);
        if (!expectedType.equals(claims.get("type", String.class))) {
            throw new BusinessException(AuthExceptionCase.TOKEN_INVALID);
        }
        return claims;
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(this.secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(AuthExceptionCase.TOKEN_EXPIRED);
        } catch (MalformedJwtException | UnsupportedJwtException | SignatureException e) {
            throw new BusinessException(AuthExceptionCase.TOKEN_INVALID);
        }
    }

}