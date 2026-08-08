package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileResponse;
import com.moduDrive.file.application.port.in.command.ListAllFilesCommand;
import com.moduDrive.file.application.port.in.usecase.ListAllFilesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class ListAllFilesController {

    private final ListAllFilesUseCase listAllFilesUseCase;

    @GetMapping("/api/v1/files/all")
    public ApiResponse<List<FileResponse>> listAllFiles(@RequestHeader("X_USER_ID") UUID userId) {
        List<FileResponse> files = listAllFilesUseCase.listAllFiles(new ListAllFilesCommand(userId))
                .stream()
                .map(FileResponse::from)
                .toList();
        return ApiResponse.success(files);
    }
}
