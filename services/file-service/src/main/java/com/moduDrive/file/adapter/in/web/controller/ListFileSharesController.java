package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileAccessListResponse;
import com.moduDrive.file.application.port.in.command.ListFileSharesCommand;
import com.moduDrive.file.application.port.in.usecase.ListFileSharesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class ListFileSharesController {

    private final ListFileSharesUseCase listFileSharesUseCase;

    @GetMapping("/api/v1/files/{fileId}/shares")
    public ApiResponse<FileAccessListResponse> listFileShares(
            @RequestHeader("X_USER_ID") UUID callerId,
            @PathVariable UUID fileId) {
        var view = listFileSharesUseCase.listFileShares(new ListFileSharesCommand(fileId, callerId));
        return ApiResponse.success(FileAccessListResponse.from(view));
    }
}
