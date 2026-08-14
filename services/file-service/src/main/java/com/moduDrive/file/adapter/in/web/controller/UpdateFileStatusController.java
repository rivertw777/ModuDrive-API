package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileResponse;
import com.moduDrive.file.adapter.in.web.dto.UpdateFileStatusRequest;
import com.moduDrive.file.application.port.in.command.UpdateFileStatusCommand;
import com.moduDrive.file.application.port.in.usecase.UpdateFileStatusUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class UpdateFileStatusController {

    private final UpdateFileStatusUseCase updateFileStatusUseCase;

    @PutMapping("/api/v1/files/{fileId}/uploaded")
    public ApiResponse<FileResponse> markUploaded(
            @RequestHeader("X_USER_ID") UUID callerId,
            @PathVariable UUID fileId,
            @Valid @RequestBody UpdateFileStatusRequest request) {
        var file = updateFileStatusUseCase.updateFileStatus(
                new UpdateFileStatusCommand(fileId, callerId, request.fileSize(), request.blockCount(), request.s3Path())
        );
        return ApiResponse.success(FileResponse.from(file));
    }
}
