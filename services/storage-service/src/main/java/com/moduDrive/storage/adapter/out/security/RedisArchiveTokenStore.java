package com.moduDrive.storage.adapter.out.security;

import com.moduDrive.common.infrastructure.redis.RedisRepository;
import com.moduDrive.storage.application.port.out.ArchiveRequest;
import com.moduDrive.storage.application.port.out.ArchiveTokenPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class RedisArchiveTokenStore implements ArchiveTokenPort {

    private static final String KEY_PREFIX = "archive-token:";
    // The browser follows the link right after prepare, so a minute is plenty.
    private static final Duration TTL = Duration.ofMinutes(1);
    private static final String NO_USER = "-";

    private final RedisRepository redisRepository;

    @Override
    public String issue(ArchiveRequest request) {
        String token = UUID.randomUUID().toString();
        // userId \n ids \n key — key last and split with a limit, since it's anonymous input
        // that may contain anything; ids and userId are already UUIDs.
        String value = (request.isPublic() ? NO_USER : request.userId().toString()) + "\n"
                + String.join(",", request.fileIds().stream().map(UUID::toString).toList()) + "\n"
                + (request.key() == null ? "" : request.key());
        redisRepository.set(KEY_PREFIX + token, value, TTL);
        return token;
    }

    @Override
    public Optional<ArchiveRequest> redeem(String token) {
        String value = redisRepository.getAndDelete(KEY_PREFIX + token);
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split("\n", 3);
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            UUID userId = NO_USER.equals(parts[0]) ? null : UUID.fromString(parts[0]);
            var fileIds = Arrays.stream(parts[1].split(",")).map(UUID::fromString).toList();
            return Optional.of(new ArchiveRequest(userId, parts[2].isEmpty() ? null : parts[2], fileIds));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
