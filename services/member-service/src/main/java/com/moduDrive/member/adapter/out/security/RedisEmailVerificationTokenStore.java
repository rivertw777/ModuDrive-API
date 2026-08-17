package com.moduDrive.member.adapter.out.security;

import com.moduDrive.common.infrastructure.redis.RedisRepository;
import com.moduDrive.member.application.port.out.EmailVerificationTokenPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
class RedisEmailVerificationTokenStore implements EmailVerificationTokenPort {

    private static final String CODE_PREFIX = "email-verify-code:";
    private static final String ATTEMPTS_PREFIX = "email-verify-attempts:";
    private static final String VERIFIED_PREFIX = "email-verified:";
    /** A 6-digit code only has 10^6 values; without a guess cap it's brute-forceable inside its TTL. */
    private static final int MAX_ATTEMPTS = 5;

    private final RedisRepository redisRepository;
    private final long tokenExpiration;

    RedisEmailVerificationTokenStore(RedisRepository redisRepository,
                                     @Value("${modudrive.member.email-verification-token-expiration}") long tokenExpiration) {
        this.redisRepository = redisRepository;
        this.tokenExpiration = tokenExpiration;
    }

    @Override
    public void saveCode(String email, String code) {
        redisRepository.set(codeKey(email), code, Duration.ofMillis(tokenExpiration));
        redisRepository.delete(attemptsKey(email));
    }

    @Override
    public boolean confirmCode(String email, String code) {
        String stored = redisRepository.get(codeKey(email));
        if (stored == null || !stored.equals(code)) {
            registerFailedAttempt(email);
            return false;
        }
        redisRepository.delete(codeKey(email));
        redisRepository.delete(attemptsKey(email));
        return true;
    }

    // ponytail: read-increment-write, not atomic under concurrent guesses — a few requests in the
    // same instant could slip past MAX_ATTEMPTS by one or two; upgrade to a Lua INCR script if
    // brute-force volume ever makes that race worth closing.
    private void registerFailedAttempt(String email) {
        String key = attemptsKey(email);
        int attempts = parseAttempts(redisRepository.get(key)) + 1;
        if (attempts >= MAX_ATTEMPTS) {
            redisRepository.delete(codeKey(email));
        }
        redisRepository.set(key, String.valueOf(attempts), Duration.ofMillis(tokenExpiration));
    }

    private int parseAttempts(String value) {
        return value == null ? 0 : Integer.parseInt(value);
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

    private String codeKey(String email) {
        return CODE_PREFIX + email;
    }

    private String attemptsKey(String email) {
        return ATTEMPTS_PREFIX + email;
    }

    private String verifiedKey(String email) {
        return VERIFIED_PREFIX + email;
    }
}
