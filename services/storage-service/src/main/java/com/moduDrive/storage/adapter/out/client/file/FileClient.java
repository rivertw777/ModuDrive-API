package com.moduDrive.storage.adapter.out.client.file;

import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.common.infrastructure.resilience4j.FeignFallbackUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "file-service", url = "${clients.file-service.url}")
interface FileClient {

    // file-service's GetLatestFileVersionsController. userId is the original caller, forwarded so
    // FileAccessGuard can enforce DOWNLOAD access.
    @GetMapping("/internal/files/{fileId}/revisions")
    @CircuitBreaker(name = "fileServiceCircuitBreaker")
    @Retry(name = "fileServiceRetry", fallbackMethod = "getFileRevisionsFallback")
    ApiResponse<List<FileVersionDto>> getFileRevisions(@PathVariable String fileId,
                                                       @RequestParam String userId,
                                                       @RequestParam(defaultValue = "1") int limit,
                                                       @RequestParam boolean markAccessed);

    default ApiResponse<List<FileVersionDto>> getFileRevisionsFallback(String fileId, String userId, int limit,
                                                               boolean markAccessed, Throwable cause) {
        return FeignFallbackUtils.handleFallback(cause);
    }

    // Anonymous download: fileId authorizes a LINK-scoped entry (or one nested under it), key a guest
    // invite. file-service's PublicFileResolver decides.
    @GetMapping("/internal/files/public/{fileId}/revisions")
    @CircuitBreaker(name = "fileServiceCircuitBreaker")
    @Retry(name = "fileServiceRetry", fallbackMethod = "getPublicFileRevisionsFallback")
    ApiResponse<List<FileVersionDto>> getPublicFileRevisions(@PathVariable String fileId,
                                                             @RequestParam(required = false) String key,
                                                             @RequestParam(defaultValue = "1") int limit);

    default ApiResponse<List<FileVersionDto>> getPublicFileRevisionsFallback(String fileId, String key, int limit,
                                                                     Throwable cause) {
        return FeignFallbackUtils.handleFallback(cause);
    }

    // Zip layout (file-service's ResolveArchiveEntriesController): checks DOWNLOAD on every picked
    // item and expands folders.
    @PostMapping("/internal/files/archive")
    @CircuitBreaker(name = "fileServiceCircuitBreaker")
    @Retry(name = "fileServiceRetry", fallbackMethod = "resolveArchiveEntriesFallback")
    ApiResponse<List<ArchiveEntryDto>> resolveArchiveEntries(@RequestBody ResolveArchiveEntriesRequest request);

    default ApiResponse<List<ArchiveEntryDto>> resolveArchiveEntriesFallback(ResolveArchiveEntriesRequest request, Throwable cause) {
        return FeignFallbackUtils.handleFallback(cause);
    }

    // Anonymous counterpart: each picked item is authorized like a single public download.
    @PostMapping("/internal/files/public/archive")
    @CircuitBreaker(name = "fileServiceCircuitBreaker")
    @Retry(name = "fileServiceRetry", fallbackMethod = "resolvePublicArchiveEntriesFallback")
    ApiResponse<List<ArchiveEntryDto>> resolvePublicArchiveEntries(@RequestBody ResolvePublicArchiveEntriesRequest request);

    default ApiResponse<List<ArchiveEntryDto>> resolvePublicArchiveEntriesFallback(ResolvePublicArchiveEntriesRequest request, Throwable cause) {
        return FeignFallbackUtils.handleFallback(cause);
    }

    // Uncommitted-upload sweep: which of these blocks file-service owns now. A read, so safe to retry.
    @PostMapping("/internal/files/blocks/committed")
    @CircuitBreaker(name = "fileServiceCircuitBreaker")
    @Retry(name = "fileServiceRetry", fallbackMethod = "findCommittedBlocksFallback")
    ApiResponse<List<String>> findCommittedBlocks(@RequestBody FindCommittedBlocksRequest request);

    default ApiResponse<List<String>> findCommittedBlocksFallback(FindCommittedBlocksRequest request, Throwable cause) {
        return FeignFallbackUtils.handleFallback(cause);
    }
}
