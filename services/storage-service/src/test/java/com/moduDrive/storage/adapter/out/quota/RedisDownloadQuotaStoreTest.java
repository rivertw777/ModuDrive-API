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
    private static final String KEY = "download-quota:user-1:files/abc/xyz";

    @BeforeEach
    void stubQuotaConfig() {
        // lenient: the "quota disabled" paths return before reading either value.
        lenient().when(storageProperties.getDownloadQuotaPerFileBytes()).thenReturn(QUOTA);
        lenient().when(storageProperties.getDownloadQuotaWindow()).thenReturn(Duration.ofHours(24));
    }

    @Nested
    @DisplayName("남은 한도를 확인할 때")
    class WhenCheckingQuota {

        @Test
        void passesWhenTheWindowHasNotBeenOpenedYet() {
            given(redisRepository.get(KEY)).willReturn(null);

            assertThatCode(() -> store.checkWithinQuota("user-1", "files/abc/xyz"))
                    .doesNotThrowAnyException();
        }

        @Test
        void passesWhileTheRecordedVolumeIsUnderTheLimit() {
            given(redisRepository.get(KEY)).willReturn(String.valueOf(QUOTA - 1));

            assertThatCode(() -> store.checkWithinQuota("user-1", "files/abc/xyz"))
                    .doesNotThrowAnyException();
        }

        @Test
        void throwsOnceTheRecordedVolumeReachesTheLimit() {
            given(redisRepository.get(KEY)).willReturn(String.valueOf(QUOTA));

            Throwable thrown = catchThrowable(() -> store.checkWithinQuota("user-1", "files/abc/xyz"));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.DOWNLOAD_QUOTA_EXCEEDED);
        }

        @Test
        void failsOpenWhenRedisIsUnavailable() {
            given(redisRepository.get(KEY)).willThrow(new QueryTimeoutException("redis down"));

            assertThatCode(() -> store.checkWithinQuota("user-1", "files/abc/xyz"))
                    .doesNotThrowAnyException();
        }

        @Test
        void failsOpenWhenTheCounterIsNotANumber() {
            given(redisRepository.get(KEY)).willReturn("garbage");

            assertThatCode(() -> store.checkWithinQuota("user-1", "files/abc/xyz"))
                    .doesNotThrowAnyException();
        }

        @Test
        void skipsRedisWhenTheQuotaIsDisabled() {
            given(storageProperties.getDownloadQuotaPerFileBytes()).willReturn(0L);

            store.checkWithinQuota("user-1", "files/abc/xyz");

            then(redisRepository).should(never()).get(any());
        }
    }

    @Nested
    @DisplayName("사용량을 기록할 때")
    class WhenRecordingUsage {

        @Test
        void incrementsTheScopedCounterByTheBytesServedWithTheWindowTtl() {
            given(redisRepository.<Long>executeScript(any(), any(), any(), any())).willReturn(1L);

            store.recordUsage("user-1", "files/abc/xyz", 4_194_304L);

            then(redisRepository).should().executeScript(
                    any(), eq(List.of(KEY)), eq("4194304"), eq("86400"));
        }

        @Test
        void recordsNothingForANonPositiveByteCount() {
            store.recordUsage("user-1", "files/abc/xyz", 0L);

            then(redisRepository).should(never()).executeScript(any(), any(), any(), any());
        }

        @Test
        void recordsNothingWhenTheQuotaIsDisabled() {
            given(storageProperties.getDownloadQuotaPerFileBytes()).willReturn(0L);

            store.recordUsage("user-1", "files/abc/xyz", 4_194_304L);

            then(redisRepository).should(never()).executeScript(any(), any(), any(), any());
        }

        @Test
        void swallowsARedisFailureRatherThanFailingTheDownload() {
            given(redisRepository.<Long>executeScript(any(), any(), any(), any()))
                    .willThrow(new QueryTimeoutException("redis down"));

            assertThatCode(() -> store.recordUsage("user-1", "files/abc/xyz", 1L))
                    .doesNotThrowAnyException();
        }
    }
}
