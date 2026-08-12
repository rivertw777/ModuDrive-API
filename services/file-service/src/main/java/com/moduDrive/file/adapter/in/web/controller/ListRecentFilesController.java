package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileResponse;
import com.moduDrive.file.application.port.in.command.ListRecentFilesCommand;
import com.moduDrive.file.application.port.in.usecase.ListRecentFilesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class ListRecentFilesController {

    private final ListRecentFilesUseCase listRecentFilesUseCase;

    @GetMapping("/api/v1/files/recent")
    public ApiResponse<List<FileResponse>> listRecentFiles(
            @RequestHeader("X_USER_ID") UUID userId,
            @RequestParam(defaultValue = "20") int limit) {
        List<FileResponse> files = listRecentFilesUseCase
                .listRecentFiles(new ListRecentFilesCommand(userId, limit))
                .stream()
                .map(FileResponse::from)
                .toList();
        return ApiResponse.success(files);
    }
}
