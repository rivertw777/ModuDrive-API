package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileShareResponse;
import com.moduDrive.file.adapter.in.web.dto.UpdateFileShareRoleRequest;
import com.moduDrive.file.application.port.in.command.RecordFileAccessCommand;
import com.moduDrive.file.application.port.in.command.UpdateFileShareRoleCommand;
import com.moduDrive.file.application.port.in.usecase.RecordFileAccessUseCase;
import com.moduDrive.file.application.port.in.usecase.UpdateFileShareRoleUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class UpdateFileShareRoleController {

    private final UpdateFileShareRoleUseCase updateFileShareRoleUseCase;
    private final RecordFileAccessUseCase recordFileAccessUseCase;

    @PatchMapping("/api/v1/files/{fileId}/shares/{shareId}")
    public ApiResponse<FileShareResponse> updateFileShareRole(
            @RequestHeader("X_USER_ID") UUID callerId,
            @PathVariable UUID fileId,
            @PathVariable UUID shareId,
            @Valid @RequestBody UpdateFileShareRoleRequest request) {
        var command = new UpdateFileShareRoleCommand(fileId, shareId, callerId, request.role());
        var share = updateFileShareRoleUseCase.updateFileShareRole(command);
        recordFileAccessUseCase.recordAccess(new RecordFileAccessCommand(callerId, fileId));
        return ApiResponse.success(FileShareResponse.from(share));
    }
}
