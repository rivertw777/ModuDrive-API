package com.moduDrive.member.adapter.out.security;

import com.moduDrive.common.infrastructure.redis.RedisRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RedisEmailVerificationTokenStoreTest {

    private static final long EXPIRATION = 30 * 60 * 1000L;
    private static final String CODE = "042917";
    private static final String EMAIL = "river@modudrive.com";
    private static final String CODE_KEY = "email-verify-code:river@modudrive.com";
    private static final String ATTEMPTS_KEY = "email-verify-attempts:river@modudrive.com";
    private static final String VERIFIED_KEY = "email-verified:river@modudrive.com";

    @Mock
    private RedisRepository redisRepository;

    private RedisEmailVerificationTokenStore store() {
        return new RedisEmailVerificationTokenStore(redisRepository, EXPIRATION);
    }

    @Nested
    @DisplayName("인증 코드를 저장할 때")
    class WhenSavingCode {

        @Test
        void writesCodeUnderEmailKeyWithExpiration() {
            store().saveCode(EMAIL, CODE);

            then(redisRepository).should().set(CODE_KEY, CODE, Duration.ofMillis(EXPIRATION));
        }
    }

    @Nested
    @DisplayName("저장된 코드와 일치하는 코드를 확인할 때")
    class WhenConfirmingMatchingCode {

        @Test
        void returnsTrueAndDeletesKey() {
            given(redisRepository.get(CODE_KEY)).willReturn(CODE);

            boolean confirmed = store().confirmCode(EMAIL, CODE);

            assertThat(confirmed).isTrue();
            then(redisRepository).should().delete(CODE_KEY);
        }
    }

    @Nested
    @DisplayName("저장된 코드와 다른 코드를 확인할 때")
    class WhenConfirmingMismatchedCode {

        @Test
        void returnsFalseAndSkipsDelete() {
            given(redisRepository.get(CODE_KEY)).willReturn(CODE);

            boolean confirmed = store().confirmCode(EMAIL, "999999");

            assertThat(confirmed).isFalse();
            then(redisRepository).should(never()).delete(anyString());
        }
    }

    @Nested
    @DisplayName("저장된 코드가 없는 이메일을 확인할 때")
    class WhenConfirmingMissingCode {

        @Test
        void returnsFalseAndSkipsDelete() {
            given(redisRepository.get(CODE_KEY)).willReturn(null);

            boolean confirmed = store().confirmCode(EMAIL, CODE);

            assertThat(confirmed).isFalse();
            then(redisRepository).should(never()).delete(anyString());
        }
    }

    @Nested
    @DisplayName("일치하지 않는 코드를 5회 연속 확인했을 때")
    class WhenConfirmingMismatchedCodeRepeatedly {

        @Test
        void deletesTheCodeAfterTheFifthFailureSoItCannotBeBruteForced() {
            given(redisRepository.get(CODE_KEY)).willReturn(CODE);
            given(redisRepository.get(ATTEMPTS_KEY)).willReturn(null, "1", "2", "3", "4");

            for (int i = 0; i < 5; i++) {
                assertThat(store().confirmCode(EMAIL, "999999")).isFalse();
            }

            then(redisRepository).should().delete(CODE_KEY);
        }
    }

    @Nested
    @DisplayName("이메일 인증 완료를 표시할 때")
    class WhenMarkingVerified {

        @Test
        void writesVerifiedFlagUnderEmailKeyWithExpiration() {
            store().markVerified(EMAIL);

            then(redisRepository).should().set(VERIFIED_KEY, "true", Duration.ofMillis(EXPIRATION));
        }
    }

    @Nested
    @DisplayName("인증 완료된 이메일을 소비할 때")
    class WhenConsumingVerified {

        @Test
        void returnsTrueAndDeletesKeyWhenPresent() {
            given(redisRepository.get(VERIFIED_KEY)).willReturn("true");

            boolean consumed = store().consumeVerified(EMAIL);

            assertThat(consumed).isTrue();
            then(redisRepository).should().delete(VERIFIED_KEY);
        }

        @Test
        void returnsFalseAndSkipsDeleteWhenAbsent() {
            given(redisRepository.get(VERIFIED_KEY)).willReturn(null);

            boolean consumed = store().consumeVerified(EMAIL);

            assertThat(consumed).isFalse();
            then(redisRepository).should(never()).delete(anyString());
        }
    }
}
