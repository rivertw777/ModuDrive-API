package com.moduDrive.member.adapter.out.security;

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

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the Lua script against a real Redis — the compare-and-count rule lives there, not in Java. */
class RedisEmailVerificationTokenStoreTest {

    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static final long EXPIRATION = 3 * 60 * 1000L;
    private static final String CODE = "042917";
    private static final String EMAIL = "river@modudrive.com";
    private static final String CODE_KEY = "email-verify-code:river@modudrive.com";
    private static final String ATTEMPTS_KEY = "email-verify-attempts:river@modudrive.com";

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisEmailVerificationTokenStore store;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        store = new RedisEmailVerificationTokenStore(new RedisRepository(redisTemplate), EXPIRATION);
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

    @Nested
    @DisplayName("인증 코드를 요청할 때")
    class WhenRequestingCode {

        @Test
        @DisplayName("한 주소에 15분에 5번까지만 허용하고, 대소문자·공백이 달라도 같이 센다")
        void allowsFiveRequestsPerAddressPerWindow() {
            for (int i = 0; i < 5; i++) {
                assertThat(store.tryRequestCode(i % 2 == 0 ? EMAIL : " River@ModuDrive.com")).isTrue();
            }

            assertThat(store.tryRequestCode(EMAIL)).isFalse();
            assertThat(store.tryRequestCode("other@modudrive.com")).isTrue();
            assertThat(redisTemplate.getExpire("email-verify-requests:river@modudrive.com")).isPositive();
        }
    }

    @Nested
    @DisplayName("인증 코드를 저장할 때")
    class WhenSavingCode {

        @Test
        @DisplayName("코드를 만료 시간과 함께 저장하고 이전 틀린 횟수를 지운다")
        void storesCodeWithExpirationAndResetsAttempts() {
            redisTemplate.opsForValue().set(ATTEMPTS_KEY, "3");

            store.saveCode(EMAIL, CODE);

            assertThat(redisTemplate.opsForValue().get(CODE_KEY)).isEqualTo(CODE);
            assertThat(redisTemplate.getExpire(CODE_KEY)).isPositive();
            assertThat(redisTemplate.hasKey(ATTEMPTS_KEY)).isFalse();
        }
    }

    @Nested
    @DisplayName("코드를 확인할 때")
    class WhenConfirming {

        @Test
        @DisplayName("맞으면 true를 주고 코드와 틀린 횟수를 지운다")
        void matchingCodeEndsTheCode() {
            store.saveCode(EMAIL, CODE);
            store.confirmCode(EMAIL, "999999");

            assertThat(store.confirmCode(EMAIL, CODE)).isTrue();
            assertThat(redisTemplate.hasKey(CODE_KEY)).isFalse();
            assertThat(redisTemplate.hasKey(ATTEMPTS_KEY)).isFalse();
        }

        @Test
        @DisplayName("틀리면 false를 주고 틀린 횟수를 만료 시간과 함께 센다")
        void wrongCodeCountsAnAttempt() {
            store.saveCode(EMAIL, CODE);

            assertThat(store.confirmCode(EMAIL, "999999")).isFalse();
            assertThat(redisTemplate.opsForValue().get(ATTEMPTS_KEY)).isEqualTo("1");
            assertThat(redisTemplate.getExpire(ATTEMPTS_KEY)).isPositive();
        }

        @Test
        @DisplayName("저장된 코드가 없으면 false를 주고 세지 않는다")
        void missingCodeIsRejectedWithoutCounting() {
            assertThat(store.confirmCode(EMAIL, CODE)).isFalse();
            assertThat(redisTemplate.hasKey(ATTEMPTS_KEY)).isFalse();
        }

        @Test
        @DisplayName("5번 틀리면 코드를 지워 맞는 코드도 거절한다")
        void fifthFailureEndsTheCode() {
            store.saveCode(EMAIL, CODE);

            for (int i = 0; i < 5; i++) {
                assertThat(store.confirmCode(EMAIL, "999999")).isFalse();
            }

            assertThat(store.confirmCode(EMAIL, CODE)).isFalse();
        }

        @Test
        @DisplayName("동시에 보낸 추측도 5번 넘게 비교되지 않는다")
        void concurrentGuessesCannotSlipPastTheLimit() throws Exception {
            store.saveCode(EMAIL, CODE);
            List<Callable<Boolean>> guesses = IntStream.range(0, 20)
                    .<Callable<Boolean>>mapToObj(i -> () -> store.confirmCode(EMAIL, "999999"))
                    .toList();

            try (ExecutorService pool = Executors.newFixedThreadPool(20)) {
                pool.invokeAll(guesses);
            }

            assertThat(redisTemplate.opsForValue().get(ATTEMPTS_KEY)).isEqualTo("5");
            assertThat(redisTemplate.hasKey(CODE_KEY)).isFalse();
        }
    }

    @Nested
    @DisplayName("인증 완료 표시를 소비할 때")
    class WhenConsumingVerified {

        @Test
        @DisplayName("표시가 있으면 true를 주고 한 번만 쓸 수 있다")
        void consumesTheFlagOnce() {
            store.markVerified(EMAIL);

            assertThat(store.consumeVerified(EMAIL)).isTrue();
            assertThat(store.consumeVerified(EMAIL)).isFalse();
        }
    }
}
