package com.moduDrive.auth.adapter.out.security;

import com.moduDrive.auth.domain.vo.DeviceId;
import com.moduDrive.auth.domain.vo.MemberEmail;
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

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the counting Lua script against a real Redis — the window rule lives there, not in Java. */
class RedisLoginAttemptStoreTest {

    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisLoginAttemptStore store;

    private final MemberEmail email = new MemberEmail("River@ModuDrive.com");
    private final DeviceId knownDevice = new DeviceId("known-device");

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        store = new RedisLoginAttemptStore(new RedisRepository(redisTemplate));
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

    private void useUpAttempts() {
        for (int i = 0; i < RedisLoginAttemptStore.MAX_ATTEMPTS; i++) {
            assertThat(store.tryAttempt(email, null)).isTrue();
        }
    }

    @Nested
    @DisplayName("한도 안에서 시도할 때")
    class WhenWithinLimit {

        @Test
        void allowsEveryAttemptAndStartsTheWindowOnce() {
            useUpAttempts();

            Long ttl = redisTemplate.getExpire("login-attempts:river@modudrive.com", TimeUnit.SECONDS);
            assertThat(ttl).isBetween(RedisLoginAttemptStore.WINDOW.toSeconds() - 5, RedisLoginAttemptStore.WINDOW.toSeconds());
        }
    }

    @Nested
    @DisplayName("한도를 넘겨 시도할 때")
    class WhenOverLimit {

        @Test
        void rejects() {
            useUpAttempts();

            assertThat(store.tryAttempt(email, null)).isFalse();
        }

        @Test
        @DisplayName("대소문자만 다른 이메일도 같은 한도를 쓴다")
        void sharesTheLimitAcrossLetterCase() {
            useUpAttempts();

            assertThat(store.tryAttempt(new MemberEmail("river@modudrive.com"), null)).isFalse();
        }
    }

    @Nested
    @DisplayName("로그인에 성공해 기록을 지웠을 때")
    class WhenCleared {

        @Test
        void allowsAgain() {
            useUpAttempts();

            store.clearAttempts(email, null);

            assertThat(store.tryAttempt(email, null)).isTrue();
        }
    }

    @Nested
    @DisplayName("아는 기기에서 시도할 때")
    class WhenFromKnownDevice {

        @Test
        @DisplayName("공용 한도를 남이 다 써도 아는 기기는 따로 센다")
        void isNotLockedByTheSharedCount() {
            useUpAttempts();

            assertThat(store.tryAttempt(email, knownDevice)).isTrue();
            assertThat(redisTemplate.keys("login-attempts:river@modudrive.com:*"))
                    .singleElement().asString().matches("login-attempts:river@modudrive.com:[0-9a-f]{64}");
        }

        @Test
        @DisplayName("아는 기기의 한도도 5번이고, 지우면 그 기기 것만 지운다")
        void hasItsOwnLimitAndClearsOnlyItself() {
            for (int i = 0; i < RedisLoginAttemptStore.MAX_ATTEMPTS; i++) {
                assertThat(store.tryAttempt(email, knownDevice)).isTrue();
            }
            assertThat(store.tryAttempt(email, knownDevice)).isFalse();
            useUpAttempts();

            store.clearAttempts(email, knownDevice);

            assertThat(store.tryAttempt(email, knownDevice)).isTrue();
            assertThat(store.tryAttempt(email, null)).isFalse();
        }
    }
}
