package com.moduDrive.member.adapter.out.security;

import com.moduDrive.common.infrastructure.redis.RedisRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RedisEmailVerificationTokenStoreTest {

    private static final long EXPIRATION = 30 * 60 * 1000L;
    private static final String TOKEN = "some-token";
    private static final String KEY = "email-verify:some-token";

    @Mock
    private RedisRepository redisRepository;

    private RedisEmailVerificationTokenStore store() {
        return new RedisEmailVerificationTokenStore(redisRepository, EXPIRATION);
    }

    @Nested
    @DisplayName("토큰을 저장할 때")
    class WhenSavingToken {

        @Test
        void writesMemberIdUnderTokenKeyWithExpiration() {
            UUID memberId = UUID.randomUUID();

            store().saveToken(TOKEN, memberId);

            then(redisRepository).should().set(KEY, memberId.toString(), Duration.ofMillis(EXPIRATION));
        }
    }

    @Nested
    @DisplayName("존재하는 토큰을 소비할 때")
    class WhenConsumingExistingToken {

        @Test
        void returnsMemberIdAndDeletesKey() {
            UUID memberId = UUID.randomUUID();
            given(redisRepository.get(KEY)).willReturn(memberId.toString());

            Optional<UUID> resolved = store().consumeToken(TOKEN);

            assertThat(resolved).contains(memberId);
            then(redisRepository).should().delete(KEY);
        }
    }

    @Nested
    @DisplayName("존재하지 않는 토큰을 소비하려 할 때")
    class WhenConsumingMissingToken {

        @Test
        void returnsEmptyAndSkipsDelete() {
            given(redisRepository.get(KEY)).willReturn(null);

            Optional<UUID> resolved = store().consumeToken(TOKEN);

            assertThat(resolved).isEmpty();
            then(redisRepository).should(never()).delete(anyString());
        }
    }
}
