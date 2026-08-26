package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileResponse;
import com.moduDrive.file.application.port.in.command.RecordFileAccessCommand;
import com.moduDrive.file.application.port.in.command.RestoreFileCommand;
import com.moduDrive.file.application.port.in.usecase.RecordFileAccessUseCase;
import com.moduDrive.file.application.port.in.usecase.RestoreFileUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class RestoreFileController {

    private final RestoreFileUseCase restoreFileUseCase;
    private final RecordFileAccessUseCase recordFileAccessUseCase;

    @PatchMapping("/api/v1/files/{fileId}/restore")
    public ApiResponse<FileResponse> restoreFile(
            @RequestHeader("X_USER_ID") UUID callerId,
            @PathVariable UUID fileId) {
        var file = restoreFileUseCase.restoreFile(new RestoreFileCommand(fileId, callerId));
        recordFileAccessUseCase.recordAccess(new RecordFileAccessCommand(callerId, fileId));
        return ApiResponse.success(FileResponse.from(file));
    }
}
