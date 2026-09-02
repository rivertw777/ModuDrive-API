package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.PublicFileResponse;
import com.moduDrive.file.application.port.in.command.ListPublicDirectoryCommand;
import com.moduDrive.file.application.port.in.usecase.ListPublicDirectoryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Reached through the gateway's {@code GET /api/v1/files/public/**} permitAll list — no
 * X_USER_ID, the directory link token is the whole credential. {@code parentId} narrows the
 * listing to a sub-directory; bound as String (like {@code token}) so a malformed value 404s
 * out of the resolver rather than 500ing on path-variable conversion. */
@WebAdapter
@RestController
@RequiredArgsConstructor
class ListPublicDirectoryController {

    private final ListPublicDirectoryUseCase listPublicDirectoryUseCase;

    @GetMapping("/api/v1/files/public/{token}/children")
    public ApiResponse<List<PublicFileResponse>> listChildren(
            @PathVariable String token,
            @RequestParam(required = false) String parentId) {
        List<PublicFileResponse> children = listPublicDirectoryUseCase
                .listPublicDirectory(new ListPublicDirectoryCommand(token, parentId))
                .stream()
                .map(PublicFileResponse::from)
                .toList();
        return ApiResponse.success(children);
    }
}
