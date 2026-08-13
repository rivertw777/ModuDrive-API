package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.application.port.in.command.RevokeFileShareCommand;
import com.moduDrive.file.application.port.in.usecase.RevokeFileShareUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class RevokeFileShareController {

    private final RevokeFileShareUseCase revokeFileShareUseCase;

    @DeleteMapping("/api/v1/files/{fileId}/shares/{shareId}")
    public ApiResponse<Void> revokeFileShare(
            @RequestHeader("X_USER_ID") UUID callerId,
            @PathVariable UUID fileId,
            @PathVariable UUID shareId) {
        revokeFileShareUseCase.revokeFileShare(new RevokeFileShareCommand(fileId, shareId, callerId));
        return ApiResponse.success();
    }
}
