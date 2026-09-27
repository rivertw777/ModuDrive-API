package com.moduDrive.auth.adapter.out.security;

import com.moduDrive.auth.domain.model.LoginChallenge;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.vo.LoginChallengeId;
import com.moduDrive.auth.domain.vo.MemberEmail;
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

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Runs the Lua scripts against a real Redis — the compare-and-count rule lives there, not in Java. */
class RedisLoginChallengeStoreTest {

    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisLoginChallengeStore store;

    private final MemberAuthData member = MemberAuthDataTestFixture.aMemberAuthDataWithRoles(List.of("MEMBER", "ADMIN"));
    private final MemberEmail email = new MemberEmail("river@modudrive.com");

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        store = new RedisLoginChallengeStore(new RedisRepository(redisTemplate));
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

    private static void assertRejectedWith(Throwable thrown, AuthExceptionCase exceptionCase) {
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getExceptionCase())
                .isEqualTo(exceptionCase);
    }

    @Nested
    @DisplayName("확인을 만들 때")
    class WhenCreating {

        @Test
        @DisplayName("무작위 ID의 해시로 10분 동안 저장한다")
        void storesUnderTheHashForTenMinutes() {
            LoginChallengeId challengeId = store.createChallenge(member, email, "042917");

            Set<String> keys = redisTemplate.keys("login-challenge:*");
            assertThat(keys).singleElement().satisfies(key -> {
                assertThat(key).matches("login-challenge:[0-9a-f]{64}").doesNotContain(challengeId.value());
                assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS)).isBetween(595_000L, 600_000L);
            });
        }
    }

    @Nested
    @DisplayName("코드가 맞을 때")
    class WhenCodeMatches {

        @Test
        @DisplayName("회원 정보와 로그인 이메일을 돌려주고 확인을 끝낸다")
        void returnsTheLoginAndEndsIt() {
            LoginChallengeId challengeId = store.createChallenge(member, email, "042917");

            LoginChallenge challenge = store.confirmChallenge(challengeId, "042917");

            assertThat(challenge.memberAuthData().getMemberId()).isEqualTo("member-id");
            assertThat(challenge.memberAuthData().getMemberRoles()).containsExactly("MEMBER", "ADMIN");
            assertThat(challenge.memberEmail()).isEqualTo(email);
            assertRejectedWith(catchThrowable(() -> store.confirmChallenge(challengeId, "042917")),
                    AuthExceptionCase.LOGIN_VERIFICATION_EXPIRED);
        }
    }

    @Nested
    @DisplayName("코드가 틀렸을 때")
    class WhenCodeIsWrong {

        @Test
        @DisplayName("틀렸다고 답하고, 아직 맞는 코드로 확인할 수 있다")
        void rejectsButKeepsTheChallenge() {
            LoginChallengeId challengeId = store.createChallenge(member, email, "042917");

            assertRejectedWith(catchThrowable(() -> store.confirmChallenge(challengeId, "000000")),
                    AuthExceptionCase.INVALID_LOGIN_VERIFICATION_CODE);

            assertThat(store.confirmChallenge(challengeId, "042917").memberEmail()).isEqualTo(email);
        }

        @Test
        @DisplayName("5번 틀리면 확인이 끝나 맞는 코드도 더는 통하지 않는다")
        void endsTheChallengeAfterFiveWrongCodes() {
            LoginChallengeId challengeId = store.createChallenge(member, email, "042917");
            for (int i = 0; i < 5; i++) {
                assertRejectedWith(catchThrowable(() -> store.confirmChallenge(challengeId, "000000")),
                        AuthExceptionCase.INVALID_LOGIN_VERIFICATION_CODE);
            }

            assertRejectedWith(catchThrowable(() -> store.confirmChallenge(challengeId, "042917")),
                    AuthExceptionCase.LOGIN_VERIFICATION_EXPIRED);
        }
    }

    @Nested
    @DisplayName("확인이 없을 때")
    class WhenChallengeIsMissing {

        @Test
        void answersExpired() {
            assertRejectedWith(catchThrowable(() -> store.confirmChallenge(new LoginChallengeId("forged"), "042917")),
                    AuthExceptionCase.LOGIN_VERIFICATION_EXPIRED);
        }
    }
}
