package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileResponse;
import com.moduDrive.file.application.port.in.command.SearchFilesCommand;
import com.moduDrive.file.application.port.in.usecase.SearchFilesUseCase;
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
class SearchFilesController {

    private final SearchFilesUseCase searchFilesUseCase;

    @GetMapping("/api/v1/files/search")
    public ApiResponse<List<FileResponse>> searchFiles(
            @RequestHeader("X_USER_ID") UUID userId,
            @RequestParam String query) {
        List<FileResponse> files = searchFilesUseCase.searchFiles(new SearchFilesCommand(userId, query))
                .stream()
                .map(FileResponse::from)
                .toList();
        return ApiResponse.success(files);
    }
}
