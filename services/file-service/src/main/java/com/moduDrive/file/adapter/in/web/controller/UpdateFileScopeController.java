package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileScopeResponse;
import com.moduDrive.file.adapter.in.web.dto.UpdateFileScopeRequest;
import com.moduDrive.file.application.port.in.command.UpdateFileScopeCommand;
import com.moduDrive.file.application.port.in.usecase.UpdateFileScopeUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class UpdateFileScopeController {

    private final UpdateFileScopeUseCase updateFileScopeUseCase;

    @PutMapping("/api/v1/files/{fileId}/scope")
    public ApiResponse<FileScopeResponse> updateFileScope(
            @RequestHeader("X_USER_ID") UUID callerId,
            @PathVariable UUID fileId,
            @Valid @RequestBody UpdateFileScopeRequest request) {
        var command = new UpdateFileScopeCommand(fileId, callerId, request.scope());
        return ApiResponse.success(FileScopeResponse.from(updateFileScopeUseCase.updateFileScope(command)));
    }
}
