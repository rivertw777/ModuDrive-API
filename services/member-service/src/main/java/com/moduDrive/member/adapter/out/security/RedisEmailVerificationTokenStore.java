package com.moduDrive.member.adapter.out.security;

import com.moduDrive.common.infrastructure.redis.RedisRepository;
import com.moduDrive.member.application.port.out.EmailVerificationTokenPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
class RedisEmailVerificationTokenStore implements EmailVerificationTokenPort {

    private static final String KEY_PREFIX = "email-verify:";

    private final RedisRepository redisRepository;
    private final long tokenExpiration;

    RedisEmailVerificationTokenStore(RedisRepository redisRepository,
                                     @Value("${modudrive.member.email-verification-token-expiration}") long tokenExpiration) {
        this.redisRepository = redisRepository;
        this.tokenExpiration = tokenExpiration;
    }

    @Override
    public void saveToken(String token, UUID memberId) {
        redisRepository.set(key(token), memberId.toString(), Duration.ofMillis(tokenExpiration));
    }

    @Override
    public Optional<UUID> consumeToken(String token) {
        String memberId = redisRepository.get(key(token));
        if (memberId == null) {
            return Optional.empty();
        }
        redisRepository.delete(key(token));
        return Optional.of(UUID.fromString(memberId));
    }

    private String key(String token) {
        return KEY_PREFIX + token;
    }
}
