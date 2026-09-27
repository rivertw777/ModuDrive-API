package com.moduDrive.auth.adapter.out.security;

import com.moduDrive.auth.application.port.out.KnownDevicePort;
import com.moduDrive.auth.domain.vo.DeviceId;
import com.moduDrive.common.infrastructure.redis.RedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * One {@code known-device:{memberId}:{sha256(deviceId)}} key per device a member verified by email
 * (spec 004 2-1). A key per device, not a set per member, so each device ages out on its own.
 */
@Component
@RequiredArgsConstructor
class RedisKnownDeviceStore implements KnownDevicePort {

    private static final String KEY_PREFIX = "known-device:";
    private static final Duration TTL = Duration.ofDays(90);

    private final RedisRepository redisRepository;

    @Override
    public boolean refreshIfKnown(String memberId, DeviceId deviceId) {
        // EXPIRE answers "does it exist" and extends it in one step.
        return redisRepository.expire(key(memberId, deviceId), TTL);
    }

    @Override
    public DeviceId remember(String memberId, DeviceId deviceId) {
        DeviceId device = deviceId != null ? deviceId : new DeviceId(SecureTokens.newToken());
        redisRepository.set(key(memberId, device), "1", TTL);
        return device;
    }

    private static String key(String memberId, DeviceId deviceId) {
        return KEY_PREFIX + memberId + ":" + SecureTokens.sha256Hex(deviceId.value());
    }
}
