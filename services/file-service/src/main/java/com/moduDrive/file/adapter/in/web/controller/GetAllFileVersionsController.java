package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileVersionResponse;
import com.moduDrive.file.application.port.in.command.GetAllFileVersionsCommand;
import com.moduDrive.file.application.port.in.usecase.GetAllFileVersionsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Service-to-service only, same reasoning as GetLatestFileVersionsController: kept off
 * {@code /api/v1/files/**} so the caller here is storage-service resolving what to purge, not
 * an end user. Deliberately a separate route rather than reusing GetLatestFileVersionsController
 * with a large limit — that one is gated on DOWNLOAD permission (which a VIEWER share holds) and
 * marks the file as recently accessed, neither of which belongs on a path that leads to
 * permanent deletion. See GetAllFileVersionsCommand. */
@WebAdapter
@RestController
@RequiredArgsConstructor
class GetAllFileVersionsController {

    private final GetAllFileVersionsUseCase getAllFileVersionsUseCase;

    @GetMapping("/internal/files/{fileId}/versions/all")
    public ApiResponse<List<FileVersionResponse>> getAllFileVersions(
            @PathVariable UUID fileId,
            @RequestParam UUID userId) {
        List<FileVersionResponse> versions = getAllFileVersionsUseCase
                .getAllFileVersions(new GetAllFileVersionsCommand(fileId, userId))
                .stream()
                .map(FileVersionResponse::from)
                .toList();
        return ApiResponse.success(versions);
    }
}
