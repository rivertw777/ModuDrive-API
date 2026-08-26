package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileShareResponse;
import com.moduDrive.file.adapter.in.web.dto.ShareFileRequest;
import com.moduDrive.file.application.port.in.command.RecordFileAccessCommand;
import com.moduDrive.file.application.port.in.command.ShareFileCommand;
import com.moduDrive.file.application.port.in.usecase.RecordFileAccessUseCase;
import com.moduDrive.file.application.port.in.usecase.ShareFileUseCase;
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
class ShareFileController {

    private final ShareFileUseCase shareFileUseCase;
    private final RecordFileAccessUseCase recordFileAccessUseCase;

    @PostMapping("/api/v1/files/{fileId}/shares")
    public ApiResponse<FileShareResponse> shareFile(
            @RequestHeader("X_USER_ID") UUID ownerId,
            @PathVariable UUID fileId,
            @Valid @RequestBody ShareFileRequest request) {
        var fileShare = shareFileUseCase.shareFile(
                new ShareFileCommand(fileId, ownerId, request.email(), request.role())
        );
        recordFileAccessUseCase.recordAccess(new RecordFileAccessCommand(ownerId, fileId));
        // Empty means a guest invite (no ModuDrive member owns the email) — nothing to return but
        // a success: the invite went out as a no-login link, not a FileShare row.
        return ApiResponse.success(fileShare.map(FileShareResponse::from).orElse(null));
    }
}
