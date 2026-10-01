package com.moduDrive.member.adapter.out.security;

import com.moduDrive.common.infrastructure.redis.RedisRepository;
import com.moduDrive.member.application.port.out.EmailVerificationTokenPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
class RedisEmailVerificationTokenStore implements EmailVerificationTokenPort {

    private static final String CODE_PREFIX = "email-verify-code:";
    private static final String ATTEMPTS_PREFIX = "email-verify-attempts:";
    private static final String VERIFIED_PREFIX = "email-verified:";
    private static final String REQUESTS_PREFIX = "email-verify-requests:";
    private static final String COOLDOWN_PREFIX = "email-verify-cooldown:";
    /** How long a code can be used — the same 5 minutes as a new-device login code (auth-service). */
    static final Duration CODE_TTL = Duration.ofMinutes(5);
    /** Codes one address can be sent per window — without it, anyone can flood a mailbox with codes. */
    static final int MAX_REQUESTS = 5;
    static final Duration REQUEST_WINDOW = Duration.ofMinutes(15);
    /** Gap between two codes for one address, so a double click or rapid resends don't each send a mail. */
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);
    /** A 6-digit code only has 10^6 values; without a guess cap it's brute-forceable inside its TTL. */
    private static final int MAX_ATTEMPTS = 5;
    /** Grace window to submit the sign-up form after verifying — independent of the (shorter) code TTL. */
    private static final Duration VERIFIED_WINDOW = Duration.ofMinutes(30);
    private static final RedisScript<Long> CONFIRM_SCRIPT =
            RedisRepository.loadScript("scripts/confirm-email-code.lua", Long.class);
    private static final RedisScript<Long> COUNT_REQUEST_SCRIPT =
            RedisRepository.loadScript("scripts/count-verification-request.lua", Long.class);

    private final RedisRepository redisRepository;

    @Override
    public CodeRequest requestCode(String email) {
        // confirmCode lifts the cooldown early once the code is matched or out of attempts.
        Long requests = redisRepository.executeScript(COUNT_REQUEST_SCRIPT,
                List.of(REQUESTS_PREFIX + normalize(email), cooldownKey(email)),
                String.valueOf(REQUEST_WINDOW.toMillis()), String.valueOf(RESEND_COOLDOWN.toMillis()));
        if (requests == null || requests == 0) {
            return CodeRequest.TOO_SOON;
        }
        return requests <= MAX_REQUESTS ? CodeRequest.ALLOWED : CodeRequest.TOO_MANY;
    }

    @Override
    public void saveCode(String email, String code) {
        redisRepository.set(codeKey(email), code, CODE_TTL);
        redisRepository.delete(attemptsKey(email));
    }

    @Override
    public CodeConfirmation confirmCode(String email, String code) {
        Long confirmed = redisRepository.executeScript(CONFIRM_SCRIPT,
                List.of(codeKey(email), attemptsKey(email), cooldownKey(email)),
                code, String.valueOf(MAX_ATTEMPTS), String.valueOf(CODE_TTL.toMillis()));
        if (confirmed == null) {
            return CodeConfirmation.EXPIRED;
        }
        return switch (confirmed.intValue()) {
            case 1 -> CodeConfirmation.MATCHED;
            case 0 -> CodeConfirmation.MISMATCHED;
            case -2 -> CodeConfirmation.EXHAUSTED;
            default -> CodeConfirmation.EXPIRED;
        };
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

    /** Keyed like the request count, so a differently-cased address can't dodge the cooldown. */
    private String cooldownKey(String email) {
        return COOLDOWN_PREFIX + normalize(email);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
