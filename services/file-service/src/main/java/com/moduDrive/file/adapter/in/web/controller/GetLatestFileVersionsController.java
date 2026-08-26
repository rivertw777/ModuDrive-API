package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileVersionResponse;
import com.moduDrive.file.application.port.in.command.GetLatestFileVersionsCommand;
import com.moduDrive.file.application.port.in.command.RecordFileAccessCommand;
import com.moduDrive.file.application.port.in.usecase.GetLatestFileVersionsUseCase;
import com.moduDrive.file.application.port.in.usecase.RecordFileAccessUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Service-to-service only: storage-service resolves the latest version to stream for download
 * through this route. Kept off {@code /api/v1/files/**} (the gateway only proxies that prefix,
 * see gateway RouteConfig) so the caller here is another trusted service, not an end user
 * directly — but the original caller's id still rides along as {@code userId} so
 * FileAccessGuard can verify that user actually holds DOWNLOAD permission before the download
 * proceeds (see #152). Same response shape as the tenant-facing {@code GET /api/v1/files/{fileId}/revisions}
 * so storage-service's DTO didn't need to change. */
@WebAdapter
@RestController
@RequiredArgsConstructor
class GetLatestFileVersionsController {

    private final GetLatestFileVersionsUseCase getLatestFileVersionsUseCase;
    private final RecordFileAccessUseCase recordFileAccessUseCase;

    @GetMapping("/internal/files/{fileId}/revisions")
    public ApiResponse<List<FileVersionResponse>> getLatestFileVersions(
            @PathVariable UUID fileId,
            @RequestParam UUID userId,
            @RequestParam(defaultValue = "1") int limit) {
        List<FileVersionResponse> revisions = getLatestFileVersionsUseCase
                .getLatestFileVersions(new GetLatestFileVersionsCommand(fileId, limit, userId))
                .stream()
                .map(FileVersionResponse::from)
                .toList();
        // Both storage-service's download and preview/view endpoints resolve the version to
        // stream through this one internal route, so this is the single choke point where an
        // actual open/download can be marked recently-accessed. RecordFileAccessUseCase never
        // throws (see RecordFileAccessService), so a tracking failure can't turn this into a 500.
        recordFileAccessUseCase.recordAccess(new RecordFileAccessCommand(userId, fileId));
        return ApiResponse.success(revisions);
    }
}
