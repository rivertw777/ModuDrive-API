package com.moduDrive.auth.adapter.out.security;

import com.moduDrive.auth.domain.model.TokenPair.TokenFamilyId;
import com.moduDrive.auth.domain.model.TokenPair.TokenJti;
import com.moduDrive.common.infrastructure.redis.RedisRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RedisTokenStoreTest {

    private static final long ONE_HOUR = 60 * 60 * 1000L;
    private static final long SEVEN_DAYS = 7 * 24 * 60 * 60 * 1000L;
    private static final String KEY = "refresh:family-id";
    private static final String BLACKLIST_KEY = "blacklist:access-jti";
    private static final String REVOKED_KEY = "revoked:family-id";

    private static final TokenFamilyId FAMILY_ID = new TokenFamilyId("family-id");
    private static final TokenJti PRESENTED_JTI = new TokenJti("presented-jti");
    private static final TokenJti NEW_JTI = new TokenJti("new-jti");
    private static final TokenJti ACCESS_JTI = new TokenJti("access-jti");

    @Mock
    private RedisRepository redisRepository;

    private RedisTokenStore store() {
        return new RedisTokenStore(redisRepository, SEVEN_DAYS, ONE_HOUR);
    }

    @Nested
    @DisplayName("리프레시 토큰을 저장할 때")
    class WhenSavingRefreshToken {

        @Test
        void writesJtiUnderFamilyKeyWithFullRefreshExpiration() {
            store().save(FAMILY_ID, PRESENTED_JTI);

            then(redisRepository).should().set(KEY, "presented-jti", Duration.ofMillis(SEVEN_DAYS));
        }
    }

    @Nested
    @DisplayName("제시된 jti가 해당 패밀리의 최신 값일 때")
    class WhenPresentedJtiIsCurrent {

        @Test
        void returnsTrue() {
            given(redisRepository.<Long>executeScript(any(RedisScript.class), any(List.class),
                    any(Object[].class))).willReturn(1L);

            boolean rotated = store().rotateIfCurrent(FAMILY_ID, PRESENTED_JTI, NEW_JTI);

            assertThat(rotated).isTrue();
            // Both the family key and its revocation tombstone key are passed, and the third arg is
            // the ACCESS-token TTL (tombstone lifetime) — the family's own TTL is carried forward
            // inside the script, never reset from Java.
            then(redisRepository).should().executeScript(any(RedisScript.class),
                    eq(List.of(KEY, REVOKED_KEY)),
                    eq("presented-jti"), eq("new-jti"), eq(String.valueOf(ONE_HOUR)));
        }
    }

    @Nested
    @DisplayName("제시된 jti가 이미 회전되어 최신 값이 아닐 때")
    class WhenPresentedJtiIsStale {

        @Test
        void returnsFalse() {
            given(redisRepository.<Long>executeScript(any(RedisScript.class), any(List.class),
                    any(Object[].class))).willReturn(0L);

            boolean rotated = store().rotateIfCurrent(FAMILY_ID, PRESENTED_JTI, NEW_JTI);

            assertThat(rotated).isFalse();
        }
    }

    @Nested
    @DisplayName("패밀리 키가 이미 사라져 스크립트가 값을 반환하지 않을 때")
    class WhenScriptReturnsNoResult {

        @Test
        void returnsFalse() {
            given(redisRepository.<Long>executeScript(any(RedisScript.class), any(List.class),
                    any(Object[].class))).willReturn(null);

            boolean rotated = store().rotateIfCurrent(FAMILY_ID, PRESENTED_JTI, NEW_JTI);

            assertThat(rotated).isFalse();
        }
    }

    @Nested
    @DisplayName("회전 스크립트를 클래스패스에서 로드할 때")
    class WhenLoadingRotateScript {

        @Test
        void resolvesTheRealLuaResource() {
            given(redisRepository.<Long>executeScript(any(RedisScript.class), any(List.class),
                    any(Object[].class))).willReturn(1L);

            store().rotateIfCurrent(FAMILY_ID, PRESENTED_JTI, NEW_JTI);

            ArgumentCaptor<RedisScript<Long>> captor = ArgumentCaptor.forClass(RedisScript.class);
            then(redisRepository).should().executeScript(captor.capture(), any(List.class), any(Object[].class));
            assertThat(captor.getValue().getScriptAsString())
                    .contains("redis.call('GET', KEYS[1])")
                    .contains("redis.call('PTTL', KEYS[1])")
                    .contains("redis.call('SET', KEYS[2], '1', 'PX', ARGV[3])");
        }
    }

    @Nested
    @DisplayName("토큰 패밀리를 폐기할 때")
    class WhenRevokingTokenFamily {

        @Test
        void deletesFamilyKey() {
            store().revoke(FAMILY_ID);

            then(redisRepository).should().delete(KEY);
        }

        @Test
        void writesRevocationTombstoneLastingOneAccessTokenLifetime() {
            store().revoke(FAMILY_ID);

            then(redisRepository).should().set(REVOKED_KEY, "1", Duration.ofMillis(ONE_HOUR));
        }
    }

    @Nested
    @DisplayName("패밀리 폐기 여부를 조회할 때")
    class WhenCheckingFamilyRevocation {

        @Test
        void returnsTrueWhenTombstoneExists() {
            given(redisRepository.hasKey(REVOKED_KEY)).willReturn(true);

            assertThat(store().isRevoked(FAMILY_ID)).isTrue();
        }

        @Test
        void returnsFalseWhenTombstoneIsAbsent() {
            given(redisRepository.hasKey(REVOKED_KEY)).willReturn(false);

            assertThat(store().isRevoked(FAMILY_ID)).isFalse();
        }
    }

    @Nested
    @DisplayName("아직 유효한 액세스 토큰을 블랙리스트에 올릴 때")
    class WhenBlacklistingUnexpiredAccessToken {

        @Test
        void writesBlacklistKeyWithRemainingTtl() {
            Date expiresAt = new Date(System.currentTimeMillis() + ONE_HOUR);

            store().blacklist(ACCESS_JTI, expiresAt);

            then(redisRepository).should().set(eq(BLACKLIST_KEY), eq("1"), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("이미 자연 만료된 액세스 토큰을 블랙리스트에 올릴 때")
    class WhenBlacklistingAlreadyExpiredAccessToken {

        @Test
        void doesNothing() {
            Date expiresAt = new Date(System.currentTimeMillis() - ONE_HOUR);

            store().blacklist(ACCESS_JTI, expiresAt);

            then(redisRepository).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("블랙리스트 등록 여부를 조회할 때")
    class WhenCheckingBlacklist {

        @Test
        void returnsTrueWhenKeyExists() {
            given(redisRepository.hasKey(BLACKLIST_KEY)).willReturn(true);

            assertThat(store().isBlacklisted(ACCESS_JTI)).isTrue();
        }

        @Test
        void returnsFalseWhenKeyIsAbsent() {
            given(redisRepository.hasKey(BLACKLIST_KEY)).willReturn(false);

            assertThat(store().isBlacklisted(ACCESS_JTI)).isFalse();
        }
    }
}
