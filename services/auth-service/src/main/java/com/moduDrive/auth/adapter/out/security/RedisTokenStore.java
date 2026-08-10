package com.moduDrive.auth.adapter.out.security;

import com.moduDrive.auth.application.port.out.BlacklistAccessTokenPort;
import com.moduDrive.auth.application.port.out.IsAccessTokenBlacklistedPort;
import com.moduDrive.auth.application.port.out.IsFamilyRevokedPort;
import com.moduDrive.auth.application.port.out.RevokeRefreshTokenPort;
import com.moduDrive.auth.application.port.out.RotateRefreshTokenPort;
import com.moduDrive.auth.application.port.out.SaveRefreshTokenPort;
import com.moduDrive.auth.domain.model.TokenPair.TokenFamilyId;
import com.moduDrive.auth.domain.model.TokenPair.TokenJti;
import com.moduDrive.common.infrastructure.redis.RedisRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;
import java.util.List;

@Component
class RedisTokenStore implements SaveRefreshTokenPort, RotateRefreshTokenPort, RevokeRefreshTokenPort,
        BlacklistAccessTokenPort, IsAccessTokenBlacklistedPort, IsFamilyRevokedPort {

    private static final String KEY_PREFIX = "refresh:";
    private static final String BLACKLIST_KEY_PREFIX = "blacklist:";
    private static final String REVOKED_KEY_PREFIX = "revoked:";

    /**
     * Compare-and-swap rotation. Swaps in the new jti only when the presented jti is the current
     * one, carrying the family's remaining TTL forward so rotation never extends the session's
     * absolute lifetime. Any other case is a reuse of an already-rotated token, which drops the
     * family key and leaves a revocation tombstone so sibling access tokens stop validating too.
     */
    private static final RedisScript<Long> ROTATE_SCRIPT =
            RedisRepository.loadScript("scripts/rotate-refresh-token.lua", Long.class);

    private final RedisRepository redisRepository;
    private final long refreshTokenExpiration;
    private final long accessTokenExpiration;

    RedisTokenStore(RedisRepository redisRepository,
                    @Value("${jwt.refreshToken.expiration}") long refreshTokenExpiration,
                    @Value("${jwt.accessToken.expiration}") long accessTokenExpiration) {
        this.redisRepository = redisRepository;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.accessTokenExpiration = accessTokenExpiration;
    }

    @Override
    public void save(TokenFamilyId familyId, TokenJti jti) {
        redisRepository.set(
                key(familyId),
                jti.getJtiValue(),
                Duration.ofMillis(refreshTokenExpiration)
        );
    }

    @Override
    public boolean rotateIfCurrent(TokenFamilyId familyId, TokenJti presentedJti, TokenJti newJti) {
        Long result = redisRepository.executeScript(
                ROTATE_SCRIPT,
                List.of(key(familyId), revokedKey(familyId)),
                presentedJti.getJtiValue(),
                newJti.getJtiValue(),
                String.valueOf(accessTokenExpiration)
        );
        return result != null && result == 1L;
    }

    @Override
    public void revoke(TokenFamilyId familyId) {
        redisRepository.delete(key(familyId));
        // Tombstone outlives every access token still in circulation for this family: the newest
        // one can have been minted just before revocation, so it expires at most one access-token
        // lifetime from now.
        redisRepository.set(
                revokedKey(familyId),
                "1",
                Duration.ofMillis(accessTokenExpiration)
        );
    }

    @Override
    public boolean isRevoked(TokenFamilyId familyId) {
        return redisRepository.hasKey(revokedKey(familyId));
    }

    @Override
    public void blacklist(TokenJti jti, Date expiresAt) {
        long ttlMillis = expiresAt.getTime() - System.currentTimeMillis();
        if (ttlMillis <= 0) {
            return; // already expired naturally, nothing to blacklist
        }
        redisRepository.set(
                blacklistKey(jti),
                "1",
                Duration.ofMillis(ttlMillis)
        );
    }

    @Override
    public boolean isBlacklisted(TokenJti jti) {
        return redisRepository.hasKey(blacklistKey(jti));
    }

    private String key(TokenFamilyId familyId) {
        return KEY_PREFIX + familyId.getFamilyIdValue();
    }

    private String blacklistKey(TokenJti jti) {
        return BLACKLIST_KEY_PREFIX + jti.getJtiValue();
    }

    private String revokedKey(TokenFamilyId familyId) {
        return REVOKED_KEY_PREFIX + familyId.getFamilyIdValue();
    }

}
