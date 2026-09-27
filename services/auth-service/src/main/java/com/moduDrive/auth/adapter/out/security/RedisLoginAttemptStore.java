package com.moduDrive.auth.adapter.out.security;

import com.moduDrive.auth.application.port.out.LoginAttemptPort;
import com.moduDrive.auth.domain.vo.DeviceId;
import com.moduDrive.auth.domain.vo.MemberEmail;
import com.moduDrive.common.infrastructure.redis.RedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Login attempts per email, counted before the password is checked (spec 004 2-1). Counting first
 * — rather than checking a failure count and bumping it afterwards — means a burst of parallel
 * guesses can't all slip past the check before the first failure lands. A known device gets its own
 * {@code login-attempts:{email}:{sha256(device)}}; everything else shares {@code login-attempts:{email}}.
 */
@Component
@RequiredArgsConstructor
class RedisLoginAttemptStore implements LoginAttemptPort {

    static final int MAX_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private static final String KEY_PREFIX = "login-attempts:";
    private static final RedisScript<Long> COUNT_SCRIPT =
            RedisRepository.loadScript("scripts/count-login-attempt.lua", Long.class);

    private final RedisRepository redisRepository;

    @Override
    public boolean tryAttempt(MemberEmail memberEmail, DeviceId knownDevice) {
        Long attempts = redisRepository.executeScript(
                COUNT_SCRIPT, List.of(key(memberEmail, knownDevice)), String.valueOf(WINDOW.toMillis()));
        return attempts != null && attempts <= MAX_ATTEMPTS;
    }

    @Override
    public void clearAttempts(MemberEmail memberEmail, DeviceId knownDevice) {
        redisRepository.delete(key(memberEmail, knownDevice));
    }

    private static String key(MemberEmail memberEmail, DeviceId knownDevice) {
        String key = KEY_PREFIX + memberEmail.normalized();
        return knownDevice == null ? key : key + ":" + SecureTokens.sha256Hex(knownDevice.value());
    }
}
