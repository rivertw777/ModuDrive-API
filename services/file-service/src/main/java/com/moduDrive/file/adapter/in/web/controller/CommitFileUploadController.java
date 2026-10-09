package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.CommitFileUploadRequest;
import com.moduDrive.file.adapter.in.web.dto.CommitFileUploadResponse;
import com.moduDrive.file.application.port.in.command.CommitFileUploadCommand;
import com.moduDrive.file.application.port.in.usecase.CommitFileUploadUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class CommitFileUploadController {

    private final CommitFileUploadUseCase commitFileUploadUseCase;

    @PostMapping("/api/v1/files/{fileId}/commit")
    public ApiResponse<CommitFileUploadResponse> commit(
            @RequestHeader("X_USER_ID") UUID userId,
            @PathVariable UUID fileId,
            @Valid @RequestBody CommitFileUploadRequest request) {
        return ApiResponse.success(CommitFileUploadResponse.from(commitFileUploadUseCase.commit(
                new CommitFileUploadCommand(fileId, userId, request.uploadId(), request.size(), request.blocklist()))));
    }
}
