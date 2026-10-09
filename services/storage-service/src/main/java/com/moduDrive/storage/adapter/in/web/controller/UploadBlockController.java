package com.moduDrive.storage.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.storage.application.port.in.command.UploadBlockCommand;
import com.moduDrive.storage.application.port.in.usecase.UploadBlockUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class UploadBlockController {

    private final UploadBlockUseCase uploadBlockUseCase;

    @PutMapping("/api/v1/storage/blocks/{hash}")
    public ApiResponse<Void> uploadBlock(
            @RequestHeader("X_USER_ID") UUID userId,
            @PathVariable String hash,
            @RequestParam MultipartFile block) throws IOException {
        uploadBlockUseCase.uploadBlock(new UploadBlockCommand(userId, hash, block.getBytes()));
        return ApiResponse.success();
    }
}
