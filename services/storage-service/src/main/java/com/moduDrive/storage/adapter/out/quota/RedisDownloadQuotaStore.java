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

    private static final RedisScript<Long> RECORD_SCRIPT =
            RedisRepository.loadScript("scripts/download-quota.lua", Long.class);

    private final RedisRepository redisRepository;
    private final StorageProperties storageProperties;

    @Override
    public void checkWithinQuota(String scope, String fileKey) {
        long limit = storageProperties.getDownloadQuotaPerFileBytes();
        if (limit <= 0) {
            return; // quota disabled
        }

        String spent;
        try {
            spent = redisRepository.get(key(scope, fileKey));
        } catch (RuntimeException redisUnavailable) {
            // Soft control: an unreachable counter must not take downloads down with it.
            log.warn("download quota check skipped, Redis unavailable (scope={}, key={})", scope, fileKey, redisUnavailable);
            return;
        }
        if (spent == null) {
            return; // window not open yet — the first request always goes through
        }
        long spentBytes;
        try {
            spentBytes = Long.parseLong(spent);
        } catch (NumberFormatException corrupted) {
            log.warn("download quota counter is not a number, ignoring (scope={}, key={}, value={})", scope, fileKey, spent);
            return;
        }
        if (spentBytes >= limit) {
            throw new BusinessException(StorageExceptionCase.DOWNLOAD_QUOTA_EXCEEDED);
        }
    }

    @Override
    public void recordUsage(String scope, String fileKey, long bytes) {
        if (bytes <= 0) {
            return;
        }
        long limit = storageProperties.getDownloadQuotaPerFileBytes();
        if (limit <= 0) {
            return; // quota disabled
        }
        long windowSeconds = Math.max(1, storageProperties.getDownloadQuotaWindow().toSeconds());
        try {
            redisRepository.executeScript(
                    RECORD_SCRIPT,
                    List.of(key(scope, fileKey)),
                    String.valueOf(bytes),
                    String.valueOf(windowSeconds));
        } catch (RuntimeException redisUnavailable) {
            log.warn("download quota usage not recorded, Redis unavailable (scope={}, key={})", scope, fileKey, redisUnavailable);
        }
    }

    private static String key(String scope, String fileKey) {
        return KEY_PREFIX + scope + ":" + fileKey;
    }
}
