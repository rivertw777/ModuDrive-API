package com.moduDrive.common.infrastructure.messaging.idempotency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("DB 없는 소비자의 처리 기록은")
class RedisProcessedEventsTest {

    private static final String QUEUE = "mail-verification-requested";
    private static final String KEY = "processed:" + QUEUE + ":outbox-1";

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RedisProcessedEvents processedEvents;

    void givenValueOperations() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        processedEvents = new RedisProcessedEvents(redisTemplate);
    }

    @Nested
    @DisplayName("이벤트를 선점할 때")
    class WhenClaiming {

        @Test
        @DisplayName("키가 없으면 심고 선점에 성공한다")
        void takesTheEventWhenTheKeyIsAbsent() {
            givenValueOperations();
            given(valueOperations.setIfAbsent(KEY, "", Duration.ofSeconds(10))).willReturn(true);

            assertThat(processedEvents.claim(QUEUE, "outbox-1")).isTrue();
        }

        @Test
        @DisplayName("같은 이벤트가 동시에 와도 한 쪽만 선점한다 — 읽고 나서 쓰는 게 아니라 SETNX 한 번이다")
        void refusesWhenSomeoneElseHasIt() {
            givenValueOperations();
            given(valueOperations.setIfAbsent(KEY, "", Duration.ofSeconds(10))).willReturn(false);

            assertThat(processedEvents.claim(QUEUE, "outbox-1")).isFalse();
        }
    }

    @Nested
    @DisplayName("선점한 일이 끝나면")
    class WhenTheWorkEnds {

        @Test
        @DisplayName("성공한 경우 보관 기간만큼 기록을 남긴다")
        void keepsTheRecordForRetention() {
            givenValueOperations();

            processedEvents.markProcessed(QUEUE, "outbox-1");

            then(valueOperations).should().set(KEY, "", ProcessedEvents.RETENTION);
        }

        @Test
        @DisplayName("실패한 경우 선점을 지워 재전송이 다시 가져가게 한다")
        void givesTheClaimBack() {
            processedEvents = new RedisProcessedEvents(redisTemplate);

            processedEvents.release(QUEUE, "outbox-1");

            then(redisTemplate).should().delete(KEY);
        }
    }
}
