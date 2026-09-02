package com.moduDrive.storage.adapter.out.quota;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.infrastructure.redis.RedisRepository;
import com.moduDrive.storage.application.port.out.DownloadQuotaPort;
import com.moduDrive.storage.config.StorageProperties;
import com.moduDrive.storage.exception.StorageExceptionCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
class RedisDownloadQuotaStore implements DownloadQuotaPort {

    private static final String KEY_PREFIX = "download-quota:";

    /** Atomic incr + first-request-only expire + limit check, so concurrent downloads of the same
     * file can't race the window open or skip the check. */
    private static final RedisScript<Long> CONSUME_SCRIPT =
            RedisRepository.loadScript("scripts/download-quota.lua", Long.class);

    private final RedisRepository redisRepository;
    private final StorageProperties storageProperties;

    @Override
    public void recordAndEnforce(String scope, String fileKey, long bytes) {
        long limit = storageProperties.getDownloadQuotaPerFileBytes();
        if (limit <= 0) {
            return; // quota disabled
        }
        long windowSeconds = Math.max(1, storageProperties.getDownloadQuotaWindow().toSeconds());

        Long withinQuota;
        try {
            withinQuota = redisRepository.executeScript(
                    CONSUME_SCRIPT,
                    List.of(KEY_PREFIX + scope + ":" + fileKey),
                    String.valueOf(bytes),
                    String.valueOf(windowSeconds),
                    String.valueOf(limit));
        } catch (RuntimeException redisUnavailable) {
            // Soft control: an unreachable counter must not take downloads down with it.
            log.warn("download quota check skipped, Redis unavailable (scope={}, key={})", scope, fileKey, redisUnavailable);
            return;
        }
        if (withinQuota == null) {
            log.warn("download quota script returned no result (scope={}, key={})", scope, fileKey);
            return;
        }
        if (withinQuota == 0L) {
            throw new BusinessException(StorageExceptionCase.DOWNLOAD_QUOTA_EXCEEDED);
        }
    }
}
