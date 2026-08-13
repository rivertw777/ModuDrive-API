package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.PublicFileResponse;
import com.moduDrive.file.application.port.in.command.GetPublicFileCommand;
import com.moduDrive.file.application.port.in.usecase.GetPublicFileUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Reached through the gateway's permitAll list, so there is no authenticated caller and
 * deliberately no X_USER_ID parameter — the token is the whole credential. Bound as String
 * (not UUID) so a malformed token 404s like any other wrong token instead of 500ing via
 * Spring's path-variable type conversion. */
@WebAdapter
@RestController
@RequiredArgsConstructor
class GetPublicFileController {

    private final GetPublicFileUseCase getPublicFileUseCase;

    @GetMapping("/api/v1/files/public/{token}")
    public ApiResponse<PublicFileResponse> getPublicFile(@PathVariable String token) {
        var file = getPublicFileUseCase.getPublicFile(new GetPublicFileCommand(token));
        return ApiResponse.success(PublicFileResponse.from(file));
    }
}
