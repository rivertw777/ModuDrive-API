package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileVersionResponse;
import com.moduDrive.file.application.port.in.command.GetPublicFileRevisionsCommand;
import com.moduDrive.file.application.port.in.usecase.GetPublicFileRevisionsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Service-to-service counterpart of {@link GetLatestFileVersionsController} for anonymous link
 * visitors: storage-service resolves the blocks to stream through this route. Same response shape
 * and {@code limit} parameter, but no {@code userId} — the token is the whole credential. */
@WebAdapter
@RestController
@RequiredArgsConstructor
class GetPublicFileRevisionsController {

    private final GetPublicFileRevisionsUseCase getPublicFileRevisionsUseCase;

    @GetMapping("/internal/files/public/{token}/revisions")
    public ApiResponse<List<FileVersionResponse>> getPublicFileRevisions(
            @PathVariable String token,
            @RequestParam(defaultValue = "1") int limit) {
        List<FileVersionResponse> revisions = getPublicFileRevisionsUseCase
                .getPublicFileRevisions(new GetPublicFileRevisionsCommand(token, limit))
                .stream()
                .map(FileVersionResponse::from)
                .toList();
        return ApiResponse.success(revisions);
    }
}
