package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.CreateDirectoryRequest;
import com.moduDrive.file.adapter.in.web.dto.FileResponse;
import com.moduDrive.file.application.port.in.command.CreateDirectoryCommand;
import com.moduDrive.file.application.port.in.command.ListDirectoryCommand;
import com.moduDrive.file.application.port.in.usecase.CreateDirectoryUseCase;
import com.moduDrive.file.application.port.in.usecase.ListDirectoryUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class DirectoryController {

    private final ListDirectoryUseCase listDirectoryUseCase;
    private final CreateDirectoryUseCase createDirectoryUseCase;

    @GetMapping("/api/v1/directories")
    public ApiResponse<List<FileResponse>> listDirectory(
            @RequestHeader("X_USER_ID") UUID userId,
            @RequestParam String path) {
        List<FileResponse> files = listDirectoryUseCase
                .listDirectory(new ListDirectoryCommand(userId, path))
                .stream()
                .map(FileResponse::from)
                .toList();
        return ApiResponse.success(files);
    }

    @PostMapping("/api/v1/directories")
    public ApiResponse<FileResponse> createDirectory(
            @RequestHeader("X_USER_ID") UUID userId,
            @Valid @RequestBody CreateDirectoryRequest request) {
        var directory = createDirectoryUseCase.createDirectory(
                new CreateDirectoryCommand(userId, request.name(), request.path(), userId)
        );
        return ApiResponse.success(FileResponse.from(directory));
    }
}
