package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileResponse;
import com.moduDrive.file.adapter.in.web.dto.MoveFileRequest;
import com.moduDrive.file.application.port.in.command.MoveFileCommand;
import com.moduDrive.file.application.port.in.command.RecordFileAccessCommand;
import com.moduDrive.file.application.port.in.usecase.MoveFileUseCase;
import com.moduDrive.file.application.port.in.usecase.RecordFileAccessUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class MoveFileController {

    private final MoveFileUseCase moveFileUseCase;
    private final RecordFileAccessUseCase recordFileAccessUseCase;

    @PatchMapping("/api/v1/files/{fileId}/path")
    public ApiResponse<FileResponse> moveFile(
            @RequestHeader("X_USER_ID") UUID callerId,
            @PathVariable UUID fileId,
            @Valid @RequestBody MoveFileRequest request) {
        var command = new MoveFileCommand(fileId, callerId, request.path());
        var file = moveFileUseCase.moveFile(command);
        recordFileAccessUseCase.recordAccess(new RecordFileAccessCommand(callerId, fileId));
        return ApiResponse.success(FileResponse.from(file));
    }
}
