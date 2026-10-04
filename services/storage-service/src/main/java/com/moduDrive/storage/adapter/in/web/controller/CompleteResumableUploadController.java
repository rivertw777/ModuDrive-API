package com.moduDrive.storage.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.storage.application.port.in.command.CompleteResumableUploadCommand;
import com.moduDrive.storage.application.port.in.usecase.CompleteResumableUploadUseCase;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class CompleteResumableUploadController {

    private final CompleteResumableUploadUseCase completeResumableUploadUseCase;

    @PostMapping("/api/v1/storage/upload/resumable/{sessionId}/complete")
    public ApiResponse<Void> completeResumableUpload(
            @RequestHeader("X_USER_ID") UUID userId,
            @PathVariable String sessionId) {
        completeResumableUploadUseCase.completeResumableUpload(
                new CompleteResumableUploadCommand(sessionId, userId));
        return ApiResponse.success();
    }
}
