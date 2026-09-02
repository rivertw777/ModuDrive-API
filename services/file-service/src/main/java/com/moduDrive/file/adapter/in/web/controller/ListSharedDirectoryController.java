package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileResponse;
import com.moduDrive.file.application.port.in.command.ListSharedDirectoryCommand;
import com.moduDrive.file.application.port.in.usecase.ListSharedDirectoryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class ListSharedDirectoryController {

    private final ListSharedDirectoryUseCase listSharedDirectoryUseCase;

    @GetMapping("/api/v1/files/{fileId}/children")
    public ApiResponse<List<FileResponse>> listChildren(
            @RequestHeader("X_USER_ID") UUID callerId,
            @PathVariable UUID fileId) {
        List<FileResponse> children = listSharedDirectoryUseCase
                .listSharedDirectory(new ListSharedDirectoryCommand(fileId, callerId))
                .stream()
                .map(FileResponse::from)
                .toList();
        return ApiResponse.success(children);
    }
}
