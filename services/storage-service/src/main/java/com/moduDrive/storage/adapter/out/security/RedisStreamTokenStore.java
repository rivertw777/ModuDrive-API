package com.moduDrive.storage.adapter.out.security;

import com.moduDrive.common.infrastructure.redis.RedisRepository;
import com.moduDrive.storage.application.port.out.StreamTokenPort;
import com.moduDrive.storage.application.port.out.StreamTokenTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class RedisStreamTokenStore implements StreamTokenPort {

    private static final String KEY_PREFIX = "stream-token:";
    // ponytail: fixed TTL; make configurable if a real need to tune session length shows up.
    private static final Duration TTL = Duration.ofMinutes(30);

    private final RedisRepository redisRepository;

    @Override
    public String issue(UUID fileId, UUID userId) {
        String token = UUID.randomUUID().toString();
        redisRepository.set(KEY_PREFIX + token, fileId + ":" + userId, TTL);
        return token;
    }

    @Override
    public Optional<StreamTokenTarget> resolve(String token) {
        String value = redisRepository.get(KEY_PREFIX + token);
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split(":", 2);
        // A malformed value (foreign key under this prefix, corrupted write) must resolve to
        // the same empty-then-401 outcome as a missing key — throwing here would leak a 500 with
        // the raw value in the response message, an oracle UNAUTHENTICATED_VIEW_REQUEST exists
        // specifically to avoid.
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            return Optional.of(new StreamTokenTarget(UUID.fromString(parts[0]), UUID.fromString(parts[1])));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
