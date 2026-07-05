package com.moduDrive.auth.adapter.out.security;

import com.moduDrive.auth.application.port.out.GenerateTokenPort;
import com.moduDrive.auth.application.port.out.ValidateTokenPort;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.model.MemberAuthData.MemberId;
import com.moduDrive.auth.domain.model.MemberAuthData.MemberRoles;
import com.moduDrive.auth.domain.model.TokenPair;
import com.moduDrive.auth.domain.model.TokenPair.AccessToken;
import com.moduDrive.auth.domain.model.TokenPair.RefreshToken;
import com.moduDrive.auth.domain.model.TokenPair.TokenGrantType;
import com.moduDrive.auth.domain.model.TokenPair.TokenIssuedAt;
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
        Date now = new Date();
        String accessToken = createAccessToken(
                memberAuthData.getMemberId(),
                memberAuthData.getMemberRoles(),
                now
        );
        String refreshToken = createRefreshToken(
                memberAuthData.getMemberId(),
                memberAuthData.getMemberRoles(),
                now
        );

        return TokenPair.create(
                new AccessToken(accessToken),
                new RefreshToken(refreshToken),
                new TokenGrantType("Bearer"),
                new TokenIssuedAt(now)
        );
    }

    private String createAccessToken(String memberId, List<String> memberRoles, Date issuedAt) {
        return Jwts.builder()
                .setSubject(memberId)
                .claim("roles", String.join(",", memberRoles))
                .setIssuedAt(issuedAt)
                .setExpiration(new Date(issuedAt.getTime() + accessTokenExpiration))
                .signWith(this.secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    private String createRefreshToken(String memberId, List<String> memberRoles, Date issuedAt) {
        return Jwts.builder()
                .setSubject(memberId)
                .claim("roles", String.join(",", memberRoles))
                .setIssuedAt(issuedAt)
                .setExpiration(new Date(issuedAt.getTime() + refreshTokenExpiration))
                .signWith(this.secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    @Override
    public MemberAuthData getMemberAuthDataFromToken(AccessToken accessToken) {
        Claims claims = parseClaims(accessToken.getTokenValue());
        String memberId = claims.getSubject();
        String rolesString = claims.get("roles", String.class);
        List<String> memberRoles = Arrays.asList(rolesString.split(","));

        return MemberAuthData.create(
                new MemberId(memberId),
                new MemberRoles(memberRoles)
        );
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