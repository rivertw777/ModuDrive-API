package com.moduDrive.common.infrastructure.messaging.idempotency;

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

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against real Redis: SETNX and the compare-and-delete script are the whole point, and neither
 * shows up against a mock. */
@DisplayName("DB 없는 소비자의 처리 기록은")
class RedisProcessedEventsTest {

    private static final String QUEUE = "mail-verification-requested";
    private static final String KEY = "processed:" + QUEUE + ":outbox-1";

    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private RedisProcessedEvents processedEvents;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void stopRedis() {
        connectionFactory.destroy();
        REDIS.stop();
    }

    @BeforeEach
    void setUp() {
        redisTemplate.delete(KEY);
        processedEvents = new RedisProcessedEvents(redisTemplate);
    }

    @Nested
    @DisplayName("이벤트를 선점할 때")
    class WhenClaiming {

        @Test
        @DisplayName("키가 없으면 짧은 리스로 심고 선점에 성공한다")
        void takesTheEventWhenTheKeyIsAbsent() {
            assertThat(processedEvents.claim(QUEUE, "outbox-1")).isTrue();
            assertThat(redisTemplate.getExpire(KEY)).isBetween(1L, 10L);
        }

        @Test
        @DisplayName("같은 이벤트는 한 쪽만 선점한다 — 읽고 나서 쓰는 게 아니라 SETNX 한 번이다")
        void refusesWhenSomeoneElseHasIt() {
            processedEvents.claim(QUEUE, "outbox-1");

            assertThat(processedEvents.claim(QUEUE, "outbox-1")).isFalse();
        }
    }

    @Nested
    @DisplayName("선점한 일이 끝나면")
    class WhenTheWorkEnds {

        @Test
        @DisplayName("성공한 경우 보관 기간만큼 기록을 남긴다")
        void keepsTheRecordForRetention() {
            processedEvents.claim(QUEUE, "outbox-1");

            processedEvents.markProcessed(QUEUE, "outbox-1");

            assertThat(redisTemplate.getExpire(KEY)).isGreaterThan(10L);
        }

        @Test
        @DisplayName("실패한 경우 선점을 지워 재전송이 다시 가져가게 한다")
        void givesTheClaimBack() {
            processedEvents.claim(QUEUE, "outbox-1");

            processedEvents.release(QUEUE, "outbox-1");

            assertThat(redisTemplate.hasKey(KEY)).isFalse();
        }

        @Test
        @DisplayName("그사이 처리 기록이 남았으면 실패로 끝나도 지우지 않는다 — 실패처럼 보였지만 실제론 나간 발송")
        void keepsARecordWrittenInTheMeantime() {
            processedEvents.claim(QUEUE, "outbox-1");
            processedEvents.markProcessed(QUEUE, "outbox-1");

            processedEvents.release(QUEUE, "outbox-1");

            assertThat(redisTemplate.hasKey(KEY)).isTrue();
        }
    }
}
