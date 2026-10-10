package com.moduDrive.member.adapter.out.security;

import io.lettuce.core.cluster.SlotHash;
import com.moduDrive.common.infrastructure.redis.RedisRepository;
import com.moduDrive.member.application.port.out.EmailVerificationTokenPort.CodeConfirmation;
import com.moduDrive.member.application.port.out.EmailVerificationTokenPort.CodeRequest;
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

    private static final String CODE = "042917";
    private static final String EMAIL = "river@modudrive.com";
    private static final String CODE_KEY = RedisEmailVerificationTokenStore.codeKey("river@modudrive.com");
    private static final String ATTEMPTS_KEY = RedisEmailVerificationTokenStore.attemptsKey("river@modudrive.com");
    private static final String COOLDOWN_KEY = RedisEmailVerificationTokenStore.cooldownKey("river@modudrive.com");

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
        store = new RedisEmailVerificationTokenStore(new RedisRepository(redisTemplate));
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
                assertThat(store.requestCode(i % 2 == 0 ? EMAIL : " River@ModuDrive.com")).isEqualTo(CodeRequest.ALLOWED);
                redisTemplate.delete(COOLDOWN_KEY);
            }

            assertThat(store.requestCode(EMAIL)).isEqualTo(CodeRequest.TOO_MANY);
            assertThat(store.requestCode("other@modudrive.com")).isEqualTo(CodeRequest.ALLOWED);
            assertThat(redisTemplate.getExpire(RedisEmailVerificationTokenStore.requestsKey("river@modudrive.com"))).isPositive();
        }

        @Test
        @DisplayName("직전 코드 후 30초 안에 다시 요청하면 거절하고, 그 요청은 15분 횟수에 세지 않는다")
        void rejectsRequestWithinCooldownWithoutCounting() {
            assertThat(store.requestCode(EMAIL)).isEqualTo(CodeRequest.ALLOWED);

            assertThat(store.requestCode(" River@ModuDrive.com")).isEqualTo(CodeRequest.TOO_SOON);
            assertThat(redisTemplate.opsForValue().get(RedisEmailVerificationTokenStore.requestsKey("river@modudrive.com"))).isEqualTo("1");
            assertThat(redisTemplate.getExpire(COOLDOWN_KEY)).isBetween(1L, 30L);

            redisTemplate.delete(COOLDOWN_KEY);
            assertThat(store.requestCode(EMAIL)).isEqualTo(CodeRequest.ALLOWED);
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
        @DisplayName("맞으면 MATCHED를 주고 코드·틀린 횟수·재전송 대기를 지운다")
        void matchingCodeEndsTheCodeAndTheCooldown() {
            store.requestCode(EMAIL);
            store.saveCode(EMAIL, CODE);
            store.confirmCode(EMAIL, "999999");

            assertThat(store.confirmCode(EMAIL, CODE)).isEqualTo(CodeConfirmation.MATCHED);
            assertThat(redisTemplate.hasKey(CODE_KEY)).isFalse();
            assertThat(redisTemplate.hasKey(ATTEMPTS_KEY)).isFalse();
            assertThat(store.requestCode(EMAIL)).isEqualTo(CodeRequest.ALLOWED);
        }

        @Test
        @DisplayName("틀리면 MISMATCHED를 주고 틀린 횟수를 만료 시간과 함께 세며, 재전송 대기는 남긴다")
        void wrongCodeCountsAnAttempt() {
            store.requestCode(EMAIL);
            store.saveCode(EMAIL, CODE);

            assertThat(store.confirmCode(EMAIL, "999999")).isEqualTo(CodeConfirmation.MISMATCHED);
            assertThat(redisTemplate.hasKey(COOLDOWN_KEY)).isTrue();
            assertThat(redisTemplate.opsForValue().get(ATTEMPTS_KEY)).isEqualTo("1");
            assertThat(redisTemplate.getExpire(ATTEMPTS_KEY)).isPositive();
        }

        @Test
        @DisplayName("저장된 코드가 없으면(만료·미발송) EXPIRED를 주고 세지 않는다")
        void missingCodeIsRejectedWithoutCounting() {
            assertThat(store.confirmCode(EMAIL, CODE)).isEqualTo(CodeConfirmation.EXPIRED);
            assertThat(redisTemplate.hasKey(ATTEMPTS_KEY)).isFalse();
        }

        @Test
        @DisplayName("5번째로 틀리면 EXHAUSTED를 주고 그 뒤로는 맞는 코드도 EXHAUSTED로 거절하며, 바로 새 코드를 받을 수 있다")
        void fifthFailureEndsTheCodeAndTheCooldown() {
            store.requestCode(EMAIL);
            store.saveCode(EMAIL, CODE);

            for (int i = 0; i < 4; i++) {
                assertThat(store.confirmCode(EMAIL, "999999")).isEqualTo(CodeConfirmation.MISMATCHED);
            }

            assertThat(store.confirmCode(EMAIL, "999999")).isEqualTo(CodeConfirmation.EXHAUSTED);
            assertThat(store.confirmCode(EMAIL, CODE)).isEqualTo(CodeConfirmation.EXHAUSTED);
            assertThat(store.requestCode(EMAIL)).isEqualTo(CodeRequest.ALLOWED);

            store.saveCode(EMAIL, "135790");
            assertThat(store.confirmCode(EMAIL, "135790")).isEqualTo(CodeConfirmation.MATCHED);
        }

        @Test
        @DisplayName("동시에 보낸 추측도 5번 넘게 비교되지 않는다")
        void concurrentGuessesCannotSlipPastTheLimit() throws Exception {
            store.saveCode(EMAIL, CODE);
            List<Callable<CodeConfirmation>> guesses = IntStream.range(0, 20)
                    .<Callable<CodeConfirmation>>mapToObj(i -> () -> store.confirmCode(EMAIL, "999999"))
                    .toList();

            try (ExecutorService pool = Executors.newFixedThreadPool(20)) {
                pool.invokeAll(guesses);
            }

            assertThat(redisTemplate.opsForValue().get(ATTEMPTS_KEY)).isEqualTo("5");
            assertThat(store.confirmCode(EMAIL, CODE)).isEqualTo(CodeConfirmation.EXHAUSTED);
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

    @Test
    @DisplayName("한 주소의 키는 대소문자와 상관없이 한 클러스터 슬롯에 있어, 여러 키를 쓰는 스크립트가 MemoryDB에서도 돈다")
    void oneAddressesKeysShareAClusterSlot() {
        int slot = SlotHash.getSlot(RedisEmailVerificationTokenStore.requestsKey("A@x.com"));

        assertThat(List.of(
                RedisEmailVerificationTokenStore.cooldownKey("a@x.com"),
                RedisEmailVerificationTokenStore.codeKey(" a@X.com"),
                RedisEmailVerificationTokenStore.attemptsKey("a@x.com")))
                .allSatisfy(key -> assertThat(SlotHash.getSlot(key)).isEqualTo(slot));
    }

    @Test
    @DisplayName("중괄호가 든 주소도 키들이 한 슬롯에 있다 — 주소를 태그에 그대로 넣지 않는다")
    void anAddressWithBracesStillSharesOneSlot() {
        assertThat(SlotHash.getSlot(RedisEmailVerificationTokenStore.requestsKey("}a@x.com")))
                .isEqualTo(SlotHash.getSlot(RedisEmailVerificationTokenStore.cooldownKey("}a@x.com")));
        assertThat(RedisEmailVerificationTokenStore.requestsKey("}a@x.com")).doesNotContain("a@x.com");
    }
}
