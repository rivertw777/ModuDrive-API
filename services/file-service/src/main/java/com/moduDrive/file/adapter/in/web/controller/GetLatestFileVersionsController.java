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

/** Service-to-service only (the gateway proxies {@code /api/v1/files/**}, not this): storage-service
 * resolves the version to stream. {@code userId} is the original caller, so the DOWNLOAD check
 * still runs. Same response shape as {@code GET /api/v1/files/{fileId}/revisions}.
 *
 * <p>{@code markAccessed} records "recent"; storage-service sets it only for an inline preview,
 * not a plain download. */
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
            @RequestParam(defaultValue = "1") int limit,
            @RequestParam(defaultValue = "false") boolean markAccessed) {
        List<FileVersionResponse> revisions = getLatestFileVersionsUseCase
                .getLatestFileVersions(new GetLatestFileVersionsCommand(fileId, limit, userId))
                .stream()
                .map(FileVersionResponse::from)
                .toList();
        // recordAccess never throws.
        if (markAccessed) {
            recordFileAccessUseCase.recordAccess(new RecordFileAccessCommand(userId, fileId));
        }
        return ApiResponse.success(revisions);
    }
}
