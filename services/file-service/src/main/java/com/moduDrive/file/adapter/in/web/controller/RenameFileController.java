package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileResponse;
import com.moduDrive.file.adapter.in.web.dto.RenameFileRequest;
import com.moduDrive.file.application.port.in.command.RenameFileCommand;
import com.moduDrive.file.application.port.in.usecase.RenameFileUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class RenameFileController {

    private final RenameFileUseCase renameFileUseCase;

    @PatchMapping("/api/v1/files/{fileId}/name")
    public ApiResponse<FileResponse> renameFile(
            @PathVariable UUID fileId,
            @Valid @RequestBody RenameFileRequest request) {
        var command = new RenameFileCommand(fileId, request.name());
        return ApiResponse.success(FileResponse.from(renameFileUseCase.renameFile(command)));
    }
}
