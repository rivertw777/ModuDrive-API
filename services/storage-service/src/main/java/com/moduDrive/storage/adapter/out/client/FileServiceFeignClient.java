package com.moduDrive.storage.adapter.out.client;

import com.moduDrive.common.core.web.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "file-service", url = "${clients.file-service.url}")
interface FileServiceFeignClient {

    // Upload-complete callback — internal so only storage-service, not an end user, can report a
    // file's size and block count (#440).
    @PutMapping("/internal/files/{fileId}/uploaded")
    void updateFileStatus(@PathVariable String fileId,
                          @RequestParam String userId,
                          @RequestBody FileUploadCallbackRequest request);

    // Internal, service-to-service route (see file-service's GetLatestFileVersionsController) —
    // not the tenant-facing /api/v1/files/{fileId}/revisions. userId is the original caller,
    // forwarded so file-service's FileAccessGuard can still enforce VIEWER access (see #152).
    @GetMapping("/internal/files/{fileId}/revisions")
    ApiResponse<List<FileVersionDto>> getFileRevisions(@PathVariable String fileId,
                                                       @RequestParam String userId,
                                                       @RequestParam(defaultValue = "1") int limit,
                                                       @RequestParam boolean markAccessed);

    // Purge-only route (see file-service's GetAllFileVersionsController) — every version, not
    // just the latest, and gated on ownership rather than DOWNLOAD permission since this feeds
    // a permanent delete.
    @GetMapping("/internal/files/{fileId}/versions/all")
    ApiResponse<List<FileVersionDto>> getAllFileVersions(@PathVariable String fileId,
                                                         @RequestParam String userId);

    // Anonymous link-share download: no userId, because there is no authenticated caller.
    // fileId alone is the credential for a LINK-scoped entry (or one nested under one); key is
    // the credential for a guest invite instead. Either way file-service (PublicFileResolver) is
    // what decides which, if any, actually authorizes this fileId.
    @GetMapping("/internal/files/public/{fileId}/revisions")
    ApiResponse<List<FileVersionDto>> getPublicFileRevisions(@PathVariable String fileId,
                                                             @RequestParam(required = false) String key,
                                                             @RequestParam(defaultValue = "1") int limit);

    // Zip download layout (see file-service's ResolveArchiveEntriesController): checks DOWNLOAD
    // on every picked item and expands folders into their contents.
    @PostMapping("/internal/files/archive")
    ApiResponse<List<ArchiveEntryDto>> resolveArchiveEntries(@RequestBody ResolveArchiveEntriesRequest request);

    // Anonymous counterpart — each picked item is authorized the way a single public download is.
    @PostMapping("/internal/files/public/archive")
    ApiResponse<List<ArchiveEntryDto>> resolvePublicArchiveEntries(@RequestBody ResolvePublicArchiveEntriesRequest request);
}
