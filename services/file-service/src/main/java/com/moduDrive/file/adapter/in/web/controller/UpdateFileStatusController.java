package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileResponse;
import com.moduDrive.file.adapter.in.web.dto.UpdateFileStatusRequest;
import com.moduDrive.file.application.port.in.command.UpdateFileStatusCommand;
import com.moduDrive.file.application.port.in.usecase.UpdateFileStatusUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Upload-complete callback from storage-service, on an internal route so only a caller holding
 * the internal token can report a file's size and block count — never an end user through the
 * gateway (#440). userId is the uploader storage-service is acting for. */
@WebAdapter
@RestController
@RequiredArgsConstructor
class UpdateFileStatusController {

    private final UpdateFileStatusUseCase updateFileStatusUseCase;

    @PutMapping("/internal/files/{fileId}/uploaded")
    public ApiResponse<FileResponse> markUploaded(
            @RequestParam UUID userId,
            @PathVariable UUID fileId,
            @Valid @RequestBody UpdateFileStatusRequest request) {
        var file = updateFileStatusUseCase.updateFileStatus(
                new UpdateFileStatusCommand(fileId, userId, request.fileSize(), request.blockCount(), request.s3Path())
        );
        return ApiResponse.success(FileResponse.from(file));
    }
}
