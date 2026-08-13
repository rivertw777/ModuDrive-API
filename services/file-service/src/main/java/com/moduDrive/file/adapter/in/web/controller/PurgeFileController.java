package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.application.port.in.command.PurgeFileCommand;
import com.moduDrive.file.application.port.in.usecase.PurgeFileUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class PurgeFileController {

    private final PurgeFileUseCase purgeFileUseCase;

    @DeleteMapping("/api/v1/files/{fileId}/purge")
    public ApiResponse<Void> purgeFile(
            @RequestHeader("X_USER_ID") UUID callerId,
            @PathVariable UUID fileId) {
        purgeFileUseCase.purgeFile(new PurgeFileCommand(fileId, callerId));
        return ApiResponse.success();
    }
}
