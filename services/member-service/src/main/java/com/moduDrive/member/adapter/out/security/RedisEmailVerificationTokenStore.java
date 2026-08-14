package com.moduDrive.member.adapter.out.security;

import com.moduDrive.common.infrastructure.redis.RedisRepository;
import com.moduDrive.member.application.port.out.EmailVerificationTokenPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
class RedisEmailVerificationTokenStore implements EmailVerificationTokenPort {

    private static final String TOKEN_PREFIX = "email-verify-token:";
    private static final String VERIFIED_PREFIX = "email-verified:";

    private final RedisRepository redisRepository;
    private final long tokenExpiration;

    RedisEmailVerificationTokenStore(RedisRepository redisRepository,
                                     @Value("${modudrive.member.email-verification-token-expiration}") long tokenExpiration) {
        this.redisRepository = redisRepository;
        this.tokenExpiration = tokenExpiration;
    }

    @Override
    public void saveToken(String token, String email) {
        redisRepository.set(tokenKey(token), email, Duration.ofMillis(tokenExpiration));
    }

    @Override
    public Optional<String> consumeToken(String token) {
        String email = redisRepository.get(tokenKey(token));
        if (email == null) {
            return Optional.empty();
        }
        redisRepository.delete(tokenKey(token));
        return Optional.of(email);
    }

    @Override
    public void markVerified(String email) {
        redisRepository.set(verifiedKey(email), "true", Duration.ofMillis(tokenExpiration));
    }

    @Override
    public boolean consumeVerified(String email) {
        boolean verified = redisRepository.get(verifiedKey(email)) != null;
        if (verified) {
            redisRepository.delete(verifiedKey(email));
        }
        return verified;
    }

    private String tokenKey(String token) {
        return TOKEN_PREFIX + token;
    }

    private String verifiedKey(String email) {
        return VERIFIED_PREFIX + email;
    }
}
