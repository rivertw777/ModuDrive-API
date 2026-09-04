package com.moduDrive.storage.adapter.out.client;

import com.moduDrive.common.core.web.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "file-service")
interface FileServiceFeignClient {

    @PutMapping("/api/v1/files/{fileId}/uploaded")
    void updateFileStatus(@PathVariable String fileId,
                          @RequestHeader("X_USER_ID") String userId,
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

    // Anonymous link-share download: no userId, because there is no authenticated caller — the
    // key is the whole credential and file-service validates it (see PublicFileResolver). fileId
    // is the shared file or one nested under a shared folder.
    @GetMapping("/internal/files/public/{fileId}/revisions")
    ApiResponse<List<FileVersionDto>> getPublicFileRevisions(@PathVariable String fileId,
                                                             @RequestParam(required = false) String key,
                                                             @RequestParam(defaultValue = "1") int limit);
}
