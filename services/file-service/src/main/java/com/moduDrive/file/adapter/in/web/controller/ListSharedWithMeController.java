package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileResponse;
import com.moduDrive.file.application.port.in.command.ListSharedWithMeCommand;
import com.moduDrive.file.application.port.in.usecase.ListSharedWithMeUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class ListSharedWithMeController {

    private final ListSharedWithMeUseCase listSharedWithMeUseCase;

    @GetMapping("/api/v1/files/shared-with-me")
    public ApiResponse<List<FileResponse>> listSharedWithMe(@RequestHeader("X_USER_ID") UUID userId) {
        List<FileResponse> files = listSharedWithMeUseCase.listSharedWithMe(new ListSharedWithMeCommand(userId))
                .stream()
                .map(FileResponse::from)
                .toList();
        return ApiResponse.success(files);
    }
}
