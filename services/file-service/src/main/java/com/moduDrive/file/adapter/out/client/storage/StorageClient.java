package com.moduDrive.file.adapter.out.client.storage;

import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.common.infrastructure.resilience4j.FeignFallbackUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "storage-service", url = "${clients.storage-service.url}")
interface StorageClient {

    // Internal route, not exposed by the gateway. A read, so safe to retry.
    @PostMapping("/internal/storage/blocks/uploaded")
    @CircuitBreaker(name = "storageServiceCircuitBreaker")
    @Retry(name = "storageServiceRetry", fallbackMethod = "findUploadedBlocksFallback")
    ApiResponse<Map<String, Integer>> findUploadedBlocks(@RequestBody FindUploadedBlocksRequest request);

    default ApiResponse<Map<String, Integer>> findUploadedBlocksFallback(FindUploadedBlocksRequest request,
                                                                        Throwable cause) {
        return FeignFallbackUtils.handleFallback(cause);
    }
}
