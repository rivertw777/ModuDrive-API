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
 * X_USER_ID, the {@code key} is the whole credential. {@code fileId} is the directory to list
 * (the link's own folder or one nested under it); both it and {@code key} are bound as String
 * so a malformed value 404s out of the resolver rather than 500ing on type conversion. */
@WebAdapter
@RestController
@RequiredArgsConstructor
class ListPublicDirectoryController {

    private final ListPublicDirectoryUseCase listPublicDirectoryUseCase;

    @GetMapping("/api/v1/files/public/{fileId}/children")
    public ApiResponse<List<PublicFileResponse>> listChildren(
            @PathVariable String fileId,
            @RequestParam(required = false) String key) {
        List<PublicFileResponse> children = listPublicDirectoryUseCase
                .listPublicDirectory(new ListPublicDirectoryCommand(fileId, key))
                .stream()
                .map(PublicFileResponse::from)
                .toList();
        return ApiResponse.success(children);
    }
}
