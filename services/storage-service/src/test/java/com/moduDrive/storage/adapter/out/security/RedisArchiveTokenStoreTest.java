package com.moduDrive.storage.adapter.out.security;

import com.moduDrive.common.infrastructure.redis.RedisRepository;
import com.moduDrive.storage.application.port.out.ArchiveRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RedisArchiveTokenStoreTest {

    @Mock private RedisRepository redisRepository;
    @InjectMocks private RedisArchiveTokenStore store;

    private String storedValue(ArchiveRequest request) {
        String token = store.issue(request);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        then(redisRepository).should().set(eq("archive-token:" + token), value.capture(), eq(Duration.ofMinutes(1)));
        given(redisRepository.getAndDelete("archive-token:" + token)).willReturn(value.getValue());
        return token;
    }

    @Nested
    @DisplayName("발급한 토큰을 사용하면")
    class WhenRedeemingAnIssuedToken {

        @Test
        void roundTripsASignedInRequest() {
            ArchiveRequest request = new ArchiveRequest(UUID.randomUUID(), null, List.of(UUID.randomUUID(), UUID.randomUUID()));

            String token = storedValue(request);

            assertThat(store.redeem(token)).contains(request);
        }

        @Test
        void roundTripsAnAnonymousRequestWhoseKeyHasSeparators() {
            ArchiveRequest request = new ArchiveRequest(null, "we\nird,key", List.of(UUID.randomUUID()));

            String token = storedValue(request);

            assertThat(store.redeem(token)).contains(request);
        }
    }

    @Nested
    @DisplayName("없거나 깨진 값이면")
    class WhenMissingOrMalformed {

        @Test
        void redeemsToEmpty() {
            given(redisRepository.getAndDelete("archive-token:gone")).willReturn(null);
            given(redisRepository.getAndDelete("archive-token:bad")).willReturn("not-a-uuid\nx\n");

            assertThat(store.redeem("gone")).isEmpty();
            assertThat(store.redeem("bad")).isEmpty();
        }
    }
}
