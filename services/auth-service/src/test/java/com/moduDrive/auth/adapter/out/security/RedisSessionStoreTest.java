package com.moduDrive.auth.adapter.out.security;

import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.model.MemberAuthData.MemberId;
import com.moduDrive.auth.domain.model.MemberAuthData.MemberRoles;
import com.moduDrive.auth.domain.vo.SessionId;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.auth.fixture.MemberAuthDataTestFixture;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.infrastructure.redis.RedisRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Runs the Lua scripts against a real Redis — the expiry rules live there, not in Java. */
class RedisSessionStoreTest {

    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration ABSOLUTE_TIMEOUT = Duration.ofHours(12);

    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisSessionStore store;

    private final MemberAuthData member = MemberAuthDataTestFixture.aMemberAuthDataWithRoles(List.of("MEMBER", "ADMIN"));

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        store = new RedisSessionStore(new RedisRepository(redisTemplate), IDLE_TIMEOUT, ABSOLUTE_TIMEOUT);
    }

    @AfterAll
    static void stopRedis() {
        connectionFactory.destroy();
        REDIS.stop();
    }

    @BeforeEach
    void flush() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    private String onlySessionKey() {
        Set<String> keys = redisTemplate.keys("session:*");
        assertThat(keys).hasSize(1);
        return keys.iterator().next();
    }

    private long ttlMillis(String key) {
        return redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
    }

    private void ageSessionBy(String key, long millis) {
        long createdAt = Long.parseLong((String) redisTemplate.opsForHash().get(key, "createdAt"));
        redisTemplate.opsForHash().put(key, "createdAt", String.valueOf(createdAt - millis));
    }

    @Nested
    @DisplayName("세션을 만들 때")
    class WhenCreating {

        @Test
        @DisplayName("256비트 무작위 ID를 만들고, Redis에는 원본이 아닌 해시로 저장한다")
        void storesOnlyTheHashOfARandomId() {
            SessionId first = store.createSession(member);
            SessionId second = store.createSession(member);

            assertThat(first.value()).hasSize(43).isNotEqualTo(second.value());
            Set<String> keys = redisTemplate.keys("session:*");
            assertThat(keys).hasSize(2)
                    .noneMatch(key -> key.contains(first.value()) || key.contains(second.value()))
                    .allMatch(key -> key.matches("session:[0-9a-f]{64}"));
        }

        @Test
        void startsWithTheIdleTimeout() {
            store.createSession(member);

            long ttl = ttlMillis(onlySessionKey());
            assertThat(ttl).isBetween(IDLE_TIMEOUT.toMillis() - 5_000, IDLE_TIMEOUT.toMillis());
        }
    }

    @Nested
    @DisplayName("살아 있는 세션을 조회할 때")
    class WhenFindingLiveSession {

        @Test
        void returnsMemberAndRoles() {
            SessionId sessionId = store.createSession(member);

            assertThat(store.findSession(sessionId, true)).hasValueSatisfying(found -> {
                assertThat(found.getMemberId()).isEqualTo("member-id");
                assertThat(found.getMemberRoles()).containsExactly("MEMBER", "ADMIN");
            });
        }

        @Test
        @DisplayName("touch=true면 유휴 만료를 다시 30분으로 늘린다")
        void touchRestartsIdleTimeout() {
            SessionId sessionId = store.createSession(member);
            String key = onlySessionKey();
            redisTemplate.expire(key, 5, TimeUnit.SECONDS);

            store.findSession(sessionId, true);

            assertThat(ttlMillis(key)).isGreaterThan(IDLE_TIMEOUT.toMillis() - 5_000);
        }

        @Test
        @DisplayName("touch=false(백그라운드 요청)면 유휴 만료를 늘리지 않는다")
        void backgroundCheckLeavesIdleTimeoutAlone() {
            SessionId sessionId = store.createSession(member);
            String key = onlySessionKey();
            redisTemplate.expire(key, 5, TimeUnit.SECONDS);

            assertThat(store.findSession(sessionId, false)).isPresent();

            assertThat(ttlMillis(key)).isLessThanOrEqualTo(5_000);
        }

        @Test
        @DisplayName("활동 중이어도 절대 만료 시각을 넘겨 늘리지 않는다")
        void touchNeverExtendsPastAbsoluteDeadline() {
            SessionId sessionId = store.createSession(member);
            String key = onlySessionKey();
            // 절대 만료까지 1분 남은 세션
            ageSessionBy(key, ABSOLUTE_TIMEOUT.toMillis() - 60_000);

            store.findSession(sessionId, true);

            assertThat(ttlMillis(key)).isLessThanOrEqualTo(60_000);
        }
    }

    @Nested
    @DisplayName("절대 만료(12시간)가 지난 세션을 조회할 때")
    class WhenAbsoluteTimeoutPassed {

        @Test
        void returnsEmptyAndDeletesTheSession() {
            SessionId sessionId = store.createSession(member);
            String key = onlySessionKey();
            ageSessionBy(key, ABSOLUTE_TIMEOUT.toMillis() + 1);

            assertThat(store.findSession(sessionId, false)).isEmpty();
            assertThat(redisTemplate.hasKey(key)).isFalse();
        }
    }

    @Nested
    @DisplayName("없는 세션 ID로 조회할 때")
    class WhenSessionIdIsUnknown {

        @Test
        void returnsEmpty() {
            store.createSession(member);

            assertThat(store.findSession(new SessionId("forged"), true)).isEmpty();
        }
    }

    @Nested
    @DisplayName("같은 회원이 다른 곳에서 다시 로그인할 때")
    class WhenSameMemberLogsInElsewhere {

        private static final Map<String, String> REPLACED_DATA = Map.of("reason", "SESSION_REPLACED");

        private void assertReplaced(SessionId sessionId, boolean touch) {
            assertThat(catchThrowable(() -> store.findSession(sessionId, touch)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        assertThat(((BusinessException) e).getExceptionCase()).isEqualTo(AuthExceptionCase.SESSION_REPLACED);
                        assertThat(((BusinessException) e).getData()).isEqualTo(REPLACED_DATA);
                    });
        }

        @Test
        @DisplayName("이전 세션은 밀려나고 새 세션만 살아 있다")
        void replacesThePreviousSession() {
            SessionId first = store.createSession(member);
            SessionId second = store.createSession(member);

            assertReplaced(first, true);
            assertThat(store.findSession(second, true)).isPresent();
        }

        @Test
        @DisplayName("밀려난 세션은 회원 정보를 잃고, 남은 TTL을 그대로 지니며 늘어나지 않는다")
        void replacedSessionKeepsItsRemainingTtlOnly() {
            SessionId first = store.createSession(member);
            String firstKey = onlySessionKey();
            redisTemplate.expire(firstKey, 5, TimeUnit.SECONDS);

            store.createSession(member);
            assertReplaced(first, true);

            assertThat(redisTemplate.opsForHash().hasKey(firstKey, "memberId")).isFalse();
            assertThat(ttlMillis(firstKey)).isBetween(1L, 5_000L);
        }

        @Test
        @DisplayName("회원별 현재 세션 키는 새 세션 해시를 가리키고 절대 만료(12시간)로 끝난다")
        void pointsTheMemberAtTheNewSession() {
            store.createSession(member);
            String key = onlySessionKey();

            assertThat(redisTemplate.opsForValue().get("member-session:member-id"))
                    .isEqualTo(key.substring("session:".length()));
            assertThat(ttlMillis("member-session:member-id"))
                    .isBetween(ABSOLUTE_TIMEOUT.toMillis() - 5_000, ABSOLUTE_TIMEOUT.toMillis());
        }

        @Test
        @DisplayName("이전 세션이 이미 사라졌으면 TTL 없는 키를 만들지 않는다")
        void leavesAnAlreadyGoneSessionAlone() {
            SessionId first = store.createSession(member);
            store.deleteSession(first);

            store.createSession(member);

            assertThat(redisTemplate.keys("session:*")).hasSize(1);
            assertThat(store.findSession(first, true)).isEmpty();
        }

        @Test
        @DisplayName("다른 회원의 세션은 건드리지 않는다")
        void leavesOtherMembersAlone() {
            SessionId other = store.createSession(MemberAuthData.create(
                    new MemberId("other-id"), new MemberRoles(List.of("MEMBER"))));

            store.createSession(member);

            assertThat(store.findSession(other, true)).isPresent();
        }
    }

    @Nested
    @DisplayName("세션을 지울 때")
    class WhenDeleting {

        @Test
        @DisplayName("바로 다음 조회부터 없는 세션이 된다")
        void makesSessionUnusableImmediately() {
            SessionId sessionId = store.createSession(member);

            store.deleteSession(sessionId);

            assertThat(store.findSession(sessionId, true)).isEmpty();
            assertThat(redisTemplate.keys("session:*")).isEmpty();
        }
    }
}
