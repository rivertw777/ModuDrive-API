package com.moduDrive.auth.adapter.out.security;

import com.moduDrive.auth.application.port.out.LoginChallengePort.CodeRequest;
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

    private LoginChallengeId withCode(String code) {
        LoginChallengeId challengeId = store.createChallenge(member, email);
        store.issueCode(challengeId, code);
        return challengeId;
    }

    private String challengeKey() {
        return redisTemplate.keys("login-challenge:*").iterator().next();
    }

    @Nested
    @DisplayName("확인을 만들 때")
    class WhenCreating {

        @Test
        @DisplayName("무작위 ID의 해시로 5분 동안 저장하고, 코드는 아직 없다")
        void storesUnderTheHashForFiveMinutes() {
            LoginChallengeId challengeId = store.createChallenge(member, email);

            Set<String> keys = redisTemplate.keys("login-challenge:*");
            assertThat(keys).singleElement().satisfies(key -> {
                assertThat(key).matches("login-challenge:[0-9a-f]{64}").doesNotContain(challengeId.value());
                assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS)).isBetween(295_000L, 300_000L);
            });
            assertThat(store.findEmail(challengeId)).isEqualTo(email);
        }

        @Test
        @DisplayName("코드를 보내기 전에 확인하면 틀린 코드로 세고, 확인은 남는다")
        void countsAGuessBeforeOneIsIssuedAsWrong() {
            LoginChallengeId challengeId = store.createChallenge(member, email);

            assertRejectedWith(catchThrowable(() -> store.confirmChallenge(challengeId, "042917")),
                    AuthExceptionCase.INVALID_LOGIN_VERIFICATION_CODE);
            assertThat(store.findEmail(challengeId)).isEqualTo(email);
        }
    }

    @Nested
    @DisplayName("코드를 보낼 때")
    class WhenIssuing {

        @Test
        @DisplayName("이전 코드는 더는 통하지 않고, 틀린 횟수와 확인의 5분이 새로 시작된다")
        void replacesTheCodeAndRestartsTheCount() {
            LoginChallengeId challengeId = withCode("042917");
            redisTemplate.expire(challengeKey(), 10, TimeUnit.SECONDS);
            for (int i = 0; i < 4; i++) {
                catchThrowable(() -> store.confirmChallenge(challengeId, "000000"));
            }

            store.issueCode(challengeId, "135790");

            assertThat(redisTemplate.getExpire(challengeKey(), TimeUnit.MILLISECONDS)).isBetween(295_000L, 300_000L);
            assertRejectedWith(catchThrowable(() -> store.confirmChallenge(challengeId, "042917")),
                    AuthExceptionCase.INVALID_LOGIN_VERIFICATION_CODE);
            assertThat(store.confirmChallenge(challengeId, "135790").memberEmail()).isEqualTo(email);
        }
    }

    @Nested
    @DisplayName("코드 발송을 요청할 때")
    class WhenRequestingCode {

        @Test
        @DisplayName("30초 안에 다시 요청하면 거절한다")
        void refusesWithinTheCooldown() {
            assertThat(store.requestCode(email)).isEqualTo(CodeRequest.ALLOWED);
            assertThat(store.requestCode(new MemberEmail(" River@ModuDrive.com"))).isEqualTo(CodeRequest.TOO_SOON);
        }

        @Test
        @DisplayName("한 주소에 15분에 5번까지만 허용한다")
        void allowsFiveRequestsPerWindow() {
            for (int i = 0; i < 5; i++) {
                assertThat(store.requestCode(email)).isEqualTo(CodeRequest.ALLOWED);
                redisTemplate.delete("login-code-cooldown:river@modudrive.com");
            }

            assertThat(store.requestCode(email)).isEqualTo(CodeRequest.TOO_MANY);
            assertThat(redisTemplate.getExpire("login-code-requests:river@modudrive.com", TimeUnit.MILLISECONDS))
                    .isBetween(895_000L, 900_000L);
        }

        @Test
        @DisplayName("코드가 끝나면(5번 틀림) 30초를 기다리지 않고 바로 다시 요청할 수 있다")
        void liftsTheCooldownOnceTheCodeEnds() {
            assertThat(store.requestCode(email)).isEqualTo(CodeRequest.ALLOWED);
            LoginChallengeId challengeId = withCode("042917");
            for (int i = 0; i < 5; i++) {
                catchThrowable(() -> store.confirmChallenge(challengeId, "000000"));
            }

            assertThat(store.requestCode(email)).isEqualTo(CodeRequest.ALLOWED);
        }
    }

    @Nested
    @DisplayName("코드가 맞을 때")
    class WhenCodeMatches {

        @Test
        @DisplayName("회원 정보와 로그인 이메일을 돌려주고 확인을 끝낸다")
        void returnsTheLoginAndEndsIt() {
            LoginChallengeId challengeId = withCode("042917");

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
            LoginChallengeId challengeId = withCode("042917");

            assertRejectedWith(catchThrowable(() -> store.confirmChallenge(challengeId, "000000")),
                    AuthExceptionCase.INVALID_LOGIN_VERIFICATION_CODE);

            assertThat(store.confirmChallenge(challengeId, "042917").memberEmail()).isEqualTo(email);
        }

        @Test
        @DisplayName("5번 틀리면 입력 횟수 초과로 맞는 코드도 통하지 않지만, 재전송한 코드로는 확인할 수 있다")
        void endsTheCodeAfterFiveWrongCodes() {
            LoginChallengeId challengeId = withCode("042917");
            for (int i = 0; i < 4; i++) {
                assertRejectedWith(catchThrowable(() -> store.confirmChallenge(challengeId, "000000")),
                        AuthExceptionCase.INVALID_LOGIN_VERIFICATION_CODE);
            }
            assertRejectedWith(catchThrowable(() -> store.confirmChallenge(challengeId, "000000")),
                    AuthExceptionCase.LOGIN_VERIFICATION_ATTEMPTS_EXCEEDED);

            assertRejectedWith(catchThrowable(() -> store.confirmChallenge(challengeId, "042917")),
                    AuthExceptionCase.LOGIN_VERIFICATION_ATTEMPTS_EXCEEDED);
            store.issueCode(challengeId, "135790");
            assertThat(store.confirmChallenge(challengeId, "135790").memberEmail()).isEqualTo(email);
        }
    }

    @Nested
    @DisplayName("확인이 없을 때")
    class WhenChallengeIsMissing {

        @Test
        void answersExpired() {
            LoginChallengeId forged = new LoginChallengeId("forged");
            assertRejectedWith(catchThrowable(() -> store.confirmChallenge(forged, "042917")),
                    AuthExceptionCase.LOGIN_VERIFICATION_EXPIRED);
            assertRejectedWith(catchThrowable(() -> store.findEmail(forged)),
                    AuthExceptionCase.LOGIN_VERIFICATION_EXPIRED);
            assertRejectedWith(catchThrowable(() -> store.issueCode(forged, "042917")),
                    AuthExceptionCase.LOGIN_VERIFICATION_EXPIRED);
            assertThat(redisTemplate.keys("login-challenge:*")).isEmpty();
        }
    }
}
