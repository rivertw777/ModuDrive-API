package com.moduDrive.auth.adapter.out.security;

import com.moduDrive.auth.domain.vo.DeviceId;
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
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RedisKnownDeviceStoreTest {

    private static final Duration TTL = Duration.ofDays(90);

    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisKnownDeviceStore store;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        store = new RedisKnownDeviceStore(new RedisRepository(redisTemplate));
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

    private String onlyKey() {
        Set<String> keys = redisTemplate.keys("known-device:*");
        assertThat(keys).hasSize(1);
        return keys.iterator().next();
    }

    @Nested
    @DisplayName("기기를 기억할 때")
    class WhenRemembering {

        @Test
        @DisplayName("기기 ID가 없으면 새 무작위 ID를 만들고, Redis에는 해시만 90일 동안 둔다")
        void mintsAnIdAndStoresOnlyItsHash() {
            DeviceId deviceId = store.remember("member-id", null);

            assertThat(deviceId.value()).hasSize(43);
            String key = onlyKey();
            assertThat(key).matches("known-device:member-id:[0-9a-f]{64}").doesNotContain(deviceId.value());
            assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS))
                    .isBetween(TTL.toMillis() - 5_000, TTL.toMillis());
        }

        @Test
        void keepsTheGivenId() {
            assertThat(store.remember("member-id", new DeviceId("device-id"))).isEqualTo(new DeviceId("device-id"));
        }
    }

    @Nested
    @DisplayName("아는 기기인지 확인할 때")
    class WhenChecking {

        @Test
        @DisplayName("기억한 기기면 true이고 90일을 다시 건다")
        void refreshesAKnownDevice() {
            DeviceId deviceId = store.remember("member-id", null);
            String key = onlyKey();
            redisTemplate.expire(key, 5, TimeUnit.SECONDS);

            assertThat(store.refreshIfKnown("member-id", deviceId)).isTrue();

            assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS)).isGreaterThan(TTL.toMillis() - 5_000);
        }

        @Test
        @DisplayName("다른 회원이 이 기기를 인증했어도 이 회원에게는 모르는 기기다")
        void isPerMember() {
            DeviceId deviceId = store.remember("other-member", null);

            assertThat(store.refreshIfKnown("member-id", deviceId)).isFalse();
            assertThat(redisTemplate.keys("known-device:member-id:*")).isEmpty();
        }

        @Test
        void unknownDeviceIsFalse() {
            assertThat(store.refreshIfKnown("member-id", new DeviceId("never-seen"))).isFalse();
        }
    }
}
