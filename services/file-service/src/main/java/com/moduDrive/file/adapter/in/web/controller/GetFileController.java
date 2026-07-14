package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileResponse;
import com.moduDrive.file.application.port.in.command.GetFileCommand;
import com.moduDrive.file.application.port.in.usecase.GetFileUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class GetFileController {

    private final GetFileUseCase getFileUseCase;

    @GetMapping("/api/v1/files/{fileId}")
    public ApiResponse<FileResponse> getFile(@PathVariable UUID fileId) {
        return ApiResponse.success(FileResponse.from(getFileUseCase.getFile(new GetFileCommand(fileId))));
    }
}
