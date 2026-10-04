package com.moduDrive.storage.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.storage.adapter.in.web.dto.InitResumableUploadRequest;
import com.moduDrive.storage.adapter.in.web.dto.ResumableUploadSessionResponse;
import com.moduDrive.storage.application.port.in.command.InitResumableUploadCommand;
import com.moduDrive.storage.application.port.in.usecase.InitResumableUploadUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class InitResumableUploadController {

    private final InitResumableUploadUseCase initResumableUploadUseCase;

    @PostMapping("/api/v1/storage/upload/resumable")
    public ApiResponse<ResumableUploadSessionResponse> initResumableUpload(
            @RequestHeader("X_USER_ID") UUID userId,
            @Valid @RequestBody InitResumableUploadRequest request) {
        UUID sessionId = initResumableUploadUseCase.initResumableUpload(
                new InitResumableUploadCommand(request.fileId(), userId, request.totalChunks(), request.fileSize()));
        return ApiResponse.success(ResumableUploadSessionResponse.of(sessionId));
    }
}
