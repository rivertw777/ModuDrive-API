package com.moduDrive.file.adapter.out.client.storage;

import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.common.infrastructure.resilience4j.FeignFallbackUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "storage-service")
interface StorageServiceClient {

    // storage-service's internal, service-to-service route (see its PurgeStoredFileController) —
    // not on /api/v1/storage/**, so only another trusted service reaches it, never an end user.
    @DeleteMapping("/internal/storage/{fileId}")
    @CircuitBreaker(name = "storageServiceCircuitBreaker", fallbackMethod = "purgeStoredFileFallback")
    @Retry(name = "storageServiceRetry")
    ApiResponse<Void> purgeStoredFile(@PathVariable String fileId, @RequestParam String userId);

    default ApiResponse<Void> purgeStoredFileFallback(String fileId, String userId, Throwable cause) {
        return FeignFallbackUtils.handleFallback(cause);
    }
}
