package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.PublicFileResponse;
import com.moduDrive.file.application.port.in.command.GetPublicFileCommand;
import com.moduDrive.file.application.port.in.usecase.GetPublicFileUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Anonymous (the gateway's permitAll list), so no X_USER_ID. {@code fileId} alone authorizes a
 * LINK-scoped entry, {@code key} a guest invite. Bound as String so a malformed value 404s like a
 * wrong one instead of failing type conversion. */
@WebAdapter
@RestController
@RequiredArgsConstructor
class GetPublicFileController {

    private final GetPublicFileUseCase getPublicFileUseCase;

    @GetMapping("/api/v1/files/public/{fileId}")
    public ApiResponse<PublicFileResponse> getPublicFile(@PathVariable String fileId,
                                                         @RequestParam(required = false) String key) {
        var file = getPublicFileUseCase.getPublicFile(new GetPublicFileCommand(fileId, key));
        return ApiResponse.success(PublicFileResponse.from(file));
    }
}
