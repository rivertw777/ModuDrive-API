package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileResponse;
import com.moduDrive.file.application.port.in.command.ListTrashCommand;
import com.moduDrive.file.application.port.in.usecase.ListTrashUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class ListTrashController {

    private final ListTrashUseCase listTrashUseCase;

    @GetMapping("/api/v1/files/trash")
    public ApiResponse<List<FileResponse>> listTrash(@RequestHeader("X_USER_ID") UUID userId) {
        List<FileResponse> files = listTrashUseCase.listTrash(new ListTrashCommand(userId))
                .stream()
                .map(FileResponse::from)
                .toList();
        return ApiResponse.success(files);
    }
}
