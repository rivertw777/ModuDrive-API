package com.moduDrive.storage.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.storage.application.port.in.command.UploadChunkCommand;
import com.moduDrive.storage.application.port.in.usecase.UploadChunkUseCase;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class UploadChunkController {

    private final UploadChunkUseCase uploadChunkUseCase;

    @PutMapping("/api/v1/storage/upload/resumable/{sessionId}")
    public ApiResponse<Void> uploadChunk(
            @RequestHeader("X_USER_ID") UUID userId,
            @PathVariable String sessionId,
            @RequestParam int chunkIndex,
            @RequestParam MultipartFile chunk) throws IOException {
        uploadChunkUseCase.uploadChunk(
                new UploadChunkCommand(sessionId, userId, chunkIndex, chunk.getBytes()));
        return ApiResponse.success();
    }
}
