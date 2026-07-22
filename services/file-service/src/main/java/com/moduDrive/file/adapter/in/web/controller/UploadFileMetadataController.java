package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileResponse;
import com.moduDrive.file.adapter.in.web.dto.UploadFileMetadataRequest;
import com.moduDrive.file.application.port.in.command.UploadFileMetadataCommand;
import com.moduDrive.file.application.port.in.usecase.UploadFileMetadataUseCase;
import com.moduDrive.file.domain.model.File.FileIsDirectory;
import com.moduDrive.file.domain.model.File.FileName;
import com.moduDrive.file.domain.model.File.FileOwnerId;
import com.moduDrive.file.domain.model.File.FilePath;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class UploadFileMetadataController {

    private final UploadFileMetadataUseCase uploadFileMetadataUseCase;

    @PostMapping("/api/v1/files/metadata")
    public ApiResponse<FileResponse> uploadFileMetadata(
            @RequestHeader("X_USER_ID") UUID userId,
            @Valid @RequestBody UploadFileMetadataRequest request) {
        var file = uploadFileMetadataUseCase.uploadFileMetadata(
                new UploadFileMetadataCommand(
                        userId,
                        new FileName(request.name()),
                        new FilePath(request.path()),
                        new FileOwnerId(userId),
                        new FileIsDirectory(Boolean.TRUE.equals(request.directory()))
                )
        );
        return ApiResponse.success(FileResponse.from(file));
    }
}
