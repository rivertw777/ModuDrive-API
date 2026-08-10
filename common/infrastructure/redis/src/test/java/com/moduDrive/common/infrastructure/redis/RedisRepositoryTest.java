package com.moduDrive.common.infrastructure.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RedisRepositoryTest {

    private static final String KEY = "some-key";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @InjectMocks
    private RedisRepository redisRepository;

    @Nested
    @DisplayName("값을 저장할 때")
    class WhenSettingValue {

        @Test
        void delegatesToValueOperationsWithTtl() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);

            redisRepository.set(KEY, "value", Duration.ofMinutes(1));

            then(valueOperations).should().set(KEY, "value", Duration.ofMinutes(1));
        }
    }

    @Nested
    @DisplayName("값을 조회할 때")
    class WhenGettingValue {

        @Test
        void returnsValueFromValueOperations() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(KEY)).willReturn("value");

            assertThat(redisRepository.get(KEY)).isEqualTo("value");
        }
    }

    @Nested
    @DisplayName("키를 삭제할 때")
    class WhenDeletingKey {

        @Test
        void delegatesToTemplate() {
            redisRepository.delete(KEY);

            then(redisTemplate).should().delete(KEY);
        }
    }

    @Nested
    @DisplayName("키 존재 여부를 확인할 때")
    class WhenCheckingKeyExistence {

        @Test
        void returnsTrueWhenTemplateReportsPresent() {
            given(redisTemplate.hasKey(KEY)).willReturn(true);

            assertThat(redisRepository.hasKey(KEY)).isTrue();
        }

        @Test
        void returnsFalseWhenTemplateReportsAbsent() {
            given(redisTemplate.hasKey(KEY)).willReturn(false);

            assertThat(redisRepository.hasKey(KEY)).isFalse();
        }

        @Test
        void returnsFalseWhenTemplateReturnsNull() {
            given(redisTemplate.hasKey(KEY)).willReturn(null);

            assertThat(redisRepository.hasKey(KEY)).isFalse();
        }
    }

    @Nested
    @DisplayName("스크립트를 실행할 때")
    class WhenExecutingScript {

        @Test
        void returnsResultFromTemplate() {
            RedisScript<Long> script = RedisRepository.loadScript("scripts/test-script.lua", Long.class);
            given(redisTemplate.<Long>execute(any(RedisScript.class), any(List.class), any(Object[].class)))
                    .willReturn(7L);

            Long result = redisRepository.executeScript(script, List.of(KEY), "arg");

            assertThat(result).isEqualTo(7L);
            then(redisTemplate).should().execute(script, List.of(KEY), "arg");
        }
    }

    @Nested
    @DisplayName("클래스패스에 존재하는 스크립트를 로드할 때")
    class WhenLoadingExistingScript {

        @Test
        void readsScriptContents() {
            RedisScript<Long> script = RedisRepository.loadScript("scripts/test-script.lua", Long.class);

            assertThat(script.getScriptAsString()).contains("redis.call('GET', KEYS[1])");
            assertThat(script.getResultType()).isEqualTo(Long.class);
        }
    }

    @Nested
    @DisplayName("클래스패스에 없는 스크립트를 로드할 때")
    class WhenLoadingMissingScript {

        @Test
        void throwsOnceScriptContentsAreRead() {
            RedisScript<Long> script = RedisRepository.loadScript("scripts/no-such-script.lua", Long.class);

            Throwable thrown = catchThrowable(script::getScriptAsString);

            assertThat(thrown).isNotNull();
        }
    }
}
