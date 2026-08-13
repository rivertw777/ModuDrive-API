package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.application.port.in.command.DeleteFileCommand;
import com.moduDrive.file.application.port.in.usecase.DeleteFileUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class DeleteFileController {

    private final DeleteFileUseCase deleteFileUseCase;

    @DeleteMapping("/api/v1/files/{fileId}")
    public ApiResponse<Void> deleteFile(
            @RequestHeader("X_USER_ID") UUID callerId,
            @PathVariable UUID fileId) {
        deleteFileUseCase.deleteFile(new DeleteFileCommand(fileId, callerId));
        return ApiResponse.success();
    }
}
