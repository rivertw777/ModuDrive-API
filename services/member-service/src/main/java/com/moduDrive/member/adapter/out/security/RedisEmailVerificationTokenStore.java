package com.moduDrive.member.adapter.out.security;

import com.moduDrive.common.infrastructure.redis.RedisRepository;
import com.moduDrive.member.application.port.out.EmailVerificationTokenPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Component
class RedisEmailVerificationTokenStore implements EmailVerificationTokenPort {

    private static final String CODE_PREFIX = "email-verify-code:";
    private static final String ATTEMPTS_PREFIX = "email-verify-attempts:";
    private static final String VERIFIED_PREFIX = "email-verified:";
    private static final String REQUESTS_PREFIX = "email-verify-requests:";
    /** Codes one address can be sent per window — without it, anyone can flood a mailbox with codes. */
    static final int MAX_REQUESTS = 5;
    static final Duration REQUEST_WINDOW = Duration.ofHours(1);
    /** A 6-digit code only has 10^6 values; without a guess cap it's brute-forceable inside its TTL. */
    private static final int MAX_ATTEMPTS = 5;
    /** Grace window to submit the sign-up form after verifying — independent of the (shorter) code TTL. */
    private static final Duration VERIFIED_WINDOW = Duration.ofMinutes(30);
    private static final RedisScript<Long> CONFIRM_SCRIPT =
            RedisRepository.loadScript("scripts/confirm-email-code.lua", Long.class);
    private static final RedisScript<Long> COUNT_REQUEST_SCRIPT =
            RedisRepository.loadScript("scripts/count-verification-request.lua", Long.class);

    private final RedisRepository redisRepository;
    private final long tokenExpiration;

    RedisEmailVerificationTokenStore(RedisRepository redisRepository,
                                     @Value("${modudrive.member.email-verification-token-expiration}") long tokenExpiration) {
        this.redisRepository = redisRepository;
        this.tokenExpiration = tokenExpiration;
    }

    @Override
    public boolean tryRequestCode(String email) {
        Long requests = redisRepository.executeScript(COUNT_REQUEST_SCRIPT,
                List.of(REQUESTS_PREFIX + email.trim().toLowerCase(Locale.ROOT)),
                String.valueOf(REQUEST_WINDOW.toMillis()));
        return requests != null && requests <= MAX_REQUESTS;
    }

    @Override
    public void saveCode(String email, String code) {
        redisRepository.set(codeKey(email), code, Duration.ofMillis(tokenExpiration));
        redisRepository.delete(attemptsKey(email));
    }

    @Override
    public boolean confirmCode(String email, String code) {
        Long confirmed = redisRepository.executeScript(CONFIRM_SCRIPT, List.of(codeKey(email), attemptsKey(email)),
                code, String.valueOf(MAX_ATTEMPTS), String.valueOf(tokenExpiration));
        return confirmed != null && confirmed == 1L;
    }

    @Override
    public void markVerified(String email) {
        redisRepository.set(verifiedKey(email), "true", VERIFIED_WINDOW);
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
