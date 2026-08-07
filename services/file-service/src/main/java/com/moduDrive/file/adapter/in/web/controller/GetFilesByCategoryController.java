package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileResponse;
import com.moduDrive.file.application.port.in.command.GetFilesByCategoryCommand;
import com.moduDrive.file.application.port.in.usecase.GetFilesByCategoryUseCase;
import com.moduDrive.file.domain.model.FileCategory;
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
class GetFilesByCategoryController {

    private final GetFilesByCategoryUseCase getFilesByCategoryUseCase;

    @GetMapping("/api/v1/files/category")
    public ApiResponse<List<FileResponse>> getFilesByCategory(
            @RequestHeader("X_USER_ID") UUID userId,
            @RequestParam("type") FileCategory category) {
        List<FileResponse> files = getFilesByCategoryUseCase
                .getFilesByCategory(new GetFilesByCategoryCommand(userId, category))
                .stream()
                .map(FileResponse::from)
                .toList();
        return ApiResponse.success(files);
    }
}
