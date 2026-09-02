package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.application.port.in.command.EmptyTrashCommand;
import com.moduDrive.file.application.port.in.usecase.EmptyTrashUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class EmptyTrashController {

    private final EmptyTrashUseCase emptyTrashUseCase;

    @DeleteMapping("/api/v1/files/trash")
    public ApiResponse<Void> emptyTrash(@RequestHeader("X_USER_ID") UUID userId) {
        emptyTrashUseCase.emptyTrash(new EmptyTrashCommand(userId));
        return ApiResponse.success();
    }
}
