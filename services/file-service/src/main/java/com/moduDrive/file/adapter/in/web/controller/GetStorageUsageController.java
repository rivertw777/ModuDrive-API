package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.StorageUsageResponse;
import com.moduDrive.file.application.port.in.command.GetStorageUsageCommand;
import com.moduDrive.file.application.port.in.usecase.GetStorageUsageUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class GetStorageUsageController {

    private final GetStorageUsageUseCase getStorageUsageUseCase;

    @GetMapping("/api/v1/files/usage")
    public ApiResponse<StorageUsageResponse> getStorageUsage(@RequestHeader("X_USER_ID") UUID userId) {
        long usedBytes = getStorageUsageUseCase.getUsedBytes(new GetStorageUsageCommand(userId));
        return ApiResponse.success(new StorageUsageResponse(usedBytes, GetStorageUsageUseCase.DEFAULT_QUOTA_BYTES));
    }
}
