package com.moduDrive.storage.adapter.out.security;

import com.moduDrive.common.infrastructure.redis.RedisRepository;
import com.moduDrive.storage.application.port.out.StreamTokenTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RedisStreamTokenStoreTest {

    @Mock private RedisRepository redisRepository;
    @InjectMocks private RedisStreamTokenStore redisStreamTokenStore;

    private final UUID fileId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Nested
    @DisplayName("토큰을 발급할 때")
    class WhenIssuing {

        @Test
        void storesTheFileAndUserIdBehindTheReturnedToken() {
            String token = redisStreamTokenStore.issue(fileId, userId);

            assertThat(token).isNotBlank();
            then(redisRepository).should().set(
                    "stream-token:" + token, fileId + ":" + userId, Duration.ofMinutes(30));
        }
    }

    @Nested
    @DisplayName("토큰을 조회할 때")
    class WhenResolving {

        @Test
        void returnsTheStoredFileAndUserId() {
            given(redisRepository.get("stream-token:tok-1")).willReturn(fileId + ":" + userId);

            Optional<StreamTokenTarget> resolved = redisStreamTokenStore.resolve("tok-1");

            assertThat(resolved).contains(new StreamTokenTarget(fileId, userId));
        }

        @Test
        void returnsEmptyForAnUnknownOrExpiredToken() {
            given(redisRepository.get(anyString())).willReturn(null);

            assertThat(redisStreamTokenStore.resolve("tok-1")).isEmpty();
        }

        @Test
        void returnsEmptyInsteadOfThrowingForAMalformedStoredValue() {
            given(redisRepository.get("stream-token:tok-1")).willReturn("not-a-valid-value");

            assertThat(redisStreamTokenStore.resolve("tok-1")).isEmpty();
        }

        @Test
        void returnsEmptyInsteadOfThrowingWhenTheStoredHalvesAreNotUuids() {
            given(redisRepository.get("stream-token:tok-1")).willReturn("not-a-uuid:also-not-a-uuid");

            assertThat(redisStreamTokenStore.resolve("tok-1")).isEmpty();
        }
    }
}
