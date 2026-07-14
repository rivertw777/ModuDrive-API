package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileShareResponse;
import com.moduDrive.file.adapter.in.web.dto.ShareFileRequest;
import com.moduDrive.file.application.port.in.command.ShareFileCommand;
import com.moduDrive.file.application.port.in.usecase.ShareFileUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class ShareFileController {

    private final ShareFileUseCase shareFileUseCase;

    @PostMapping("/api/v1/files/{fileId}/share")
    public ApiResponse<FileShareResponse> shareFile(
            @PathVariable UUID fileId,
            @Valid @RequestBody ShareFileRequest request) {
        var fileShare = shareFileUseCase.shareFile(
                new ShareFileCommand(fileId, request.ownerId(), request.sharedWithUserId(), request.permission())
        );
        return ApiResponse.success(FileShareResponse.from(fileShare));
    }
}
