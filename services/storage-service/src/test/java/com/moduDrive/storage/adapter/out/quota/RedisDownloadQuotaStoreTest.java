package com.moduDrive.storage.adapter.out.quota;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.infrastructure.redis.RedisRepository;
import com.moduDrive.storage.config.StorageProperties;
import com.moduDrive.storage.exception.StorageExceptionCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RedisDownloadQuotaStoreTest {

    @Mock private RedisRepository redisRepository;
    @Mock private StorageProperties storageProperties;
    @InjectMocks private RedisDownloadQuotaStore store;

    private static final long QUOTA = 10L * 1024 * 1024 * 1024;

    @BeforeEach
    void stubQuotaConfig() {
        // lenient: the "quota disabled" path returns before reading the window.
        lenient().when(storageProperties.getDownloadQuotaPerFileBytes()).thenReturn(QUOTA);
        lenient().when(storageProperties.getDownloadQuotaWindow()).thenReturn(Duration.ofHours(24));
    }

    @Nested
    @DisplayName("파일이 아직 한도 내일 때")
    class WhenWithinQuota {

        @Test
        void recordsAgainstAScopedKeyAndDoesNotThrow() {
            given(redisRepository.<Long>executeScript(any(), any(), any(), any(), any())).willReturn(1L);

            assertThatCode(() -> store.recordAndEnforce("user-1", "files/abc/xyz", 4_194_304L))
                    .doesNotThrowAnyException();

            then(redisRepository).should().executeScript(
                    any(),
                    eq(List.of("download-quota:user-1:files/abc/xyz")),
                    eq("4194304"), eq("86400"), eq(String.valueOf(QUOTA)));
        }
    }

    @Nested
    @DisplayName("이번 요청이 한도를 넘길 때")
    class WhenQuotaExceeded {

        @Test
        void throwsDownloadQuotaExceeded() {
            given(redisRepository.<Long>executeScript(any(), any(), any(), any(), any())).willReturn(0L);

            Throwable thrown = catchThrowable(() -> store.recordAndEnforce("tok-1", "files/abc/xyz", 4_194_304L));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.DOWNLOAD_QUOTA_EXCEEDED);
        }
    }

    @Nested
    @DisplayName("쿼터 카운터를 신뢰할 수 없을 때 — 다운로드를 막지 않는다")
    class WhenTheCounterIsUnreliable {

        @Test
        void failsOpenWhenRedisIsUnavailable() {
            given(redisRepository.<Long>executeScript(any(), any(), any(), any(), any()))
                    .willThrow(new QueryTimeoutException("redis down"));

            assertThatCode(() -> store.recordAndEnforce("user-1", "files/abc/xyz", 1L))
                    .doesNotThrowAnyException();
        }

        @Test
        void failsOpenWhenTheScriptReturnsNoResult() {
            given(redisRepository.<Long>executeScript(any(), any(), any(), any(), any())).willReturn(null);

            assertThatCode(() -> store.recordAndEnforce("user-1", "files/abc/xyz", 1L))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("쿼터가 비활성화되어 있을 때 (0 이하)")
    class WhenQuotaDisabled {

        @Test
        void skipsRedisEntirely() {
            given(storageProperties.getDownloadQuotaPerFileBytes()).willReturn(0L);

            store.recordAndEnforce("user-1", "files/abc/xyz", 4_194_304L);

            then(redisRepository).should(never()).executeScript(any(), any(), any(), any(), any());
        }
    }
}
