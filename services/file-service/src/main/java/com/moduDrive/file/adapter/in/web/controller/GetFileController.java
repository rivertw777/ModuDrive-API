package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileResponse;
import com.moduDrive.file.application.port.in.command.GetFileCommand;
import com.moduDrive.file.application.port.in.command.RecordFileAccessCommand;
import com.moduDrive.file.application.port.in.usecase.GetFileUseCase;
import com.moduDrive.file.application.port.in.usecase.RecordFileAccessUseCase;
import com.moduDrive.file.domain.model.File;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class GetFileController {

    private final GetFileUseCase getFileUseCase;
    private final RecordFileAccessUseCase recordFileAccessUseCase;

    @GetMapping("/api/v1/files/{fileId}")
    public ApiResponse<FileResponse> getFile(
            @RequestHeader("X_USER_ID") UUID userId,
            @PathVariable UUID fileId) {
        File file = getFileUseCase.getFile(new GetFileCommand(fileId, userId));
        // RecordFileAccessUseCase never throws (see RecordFileAccessService) — a tracking
        // failure must never turn an already-successful read into a 500.
        recordFileAccessUseCase.recordAccess(new RecordFileAccessCommand(userId, fileId));
        return ApiResponse.success(FileResponse.from(file));
    }
}
