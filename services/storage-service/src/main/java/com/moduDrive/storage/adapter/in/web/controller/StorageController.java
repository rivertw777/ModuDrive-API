package com.moduDrive.storage.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.storage.application.port.in.command.SimpleUploadCommand;
import com.moduDrive.storage.application.port.in.usecase.SimpleUploadUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@WebAdapter
@RestController
@RequiredArgsConstructor
class StorageController {

    private final SimpleUploadUseCase simpleUploadUseCase;

    @PostMapping("/api/v1/storage/upload")
    public ApiResponse<Void> simpleUpload(
            @RequestParam String fileId,
            @RequestParam MultipartFile file) throws IOException {
        simpleUploadUseCase.simpleUpload(new SimpleUploadCommand(fileId, file.getBytes()));
        return ApiResponse.success();
    }
}
