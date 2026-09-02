package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileVersionResponse;
import com.moduDrive.file.application.port.in.command.GetPublicDescendantVersionsCommand;
import com.moduDrive.file.application.port.in.usecase.GetPublicDescendantVersionsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Service-to-service counterpart of {@link GetPublicFileRevisionsController} for a file nested
 * under a link-shared folder: storage-service passes the folder token plus the descendant's id.
 * {@code /internal} route, not gateway-exposed. */
@WebAdapter
@RestController
@RequiredArgsConstructor
class GetPublicDescendantVersionsController {

    private final GetPublicDescendantVersionsUseCase getPublicDescendantVersionsUseCase;

    @GetMapping("/internal/files/public/{token}/entry/{entryId}/revisions")
    public ApiResponse<List<FileVersionResponse>> getPublicDescendantRevisions(
            @PathVariable String token,
            @PathVariable String entryId,
            @RequestParam(defaultValue = "1") int limit) {
        List<FileVersionResponse> revisions = getPublicDescendantVersionsUseCase
                .getPublicDescendantVersions(new GetPublicDescendantVersionsCommand(token, entryId, limit))
                .stream()
                .map(FileVersionResponse::from)
                .toList();
        return ApiResponse.success(revisions);
    }
}
