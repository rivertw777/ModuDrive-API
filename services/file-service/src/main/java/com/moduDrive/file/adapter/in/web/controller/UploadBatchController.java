package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.UploadBatchRequest;
import com.moduDrive.file.adapter.in.web.dto.UploadBatchResponse;
import com.moduDrive.file.application.port.in.command.UploadBatchCommand;
import com.moduDrive.file.application.port.in.usecase.UploadBatchUseCase;
import com.moduDrive.file.application.port.in.usecase.UploadBatchUseCase.UploadedItem;
import com.moduDrive.file.domain.model.File.FilePath;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class UploadBatchController {

    private final UploadBatchUseCase uploadBatchUseCase;

    @PostMapping("/api/v1/files/batch")
    public ApiResponse<UploadBatchResponse> uploadBatch(
            @RequestHeader("X_USER_ID") UUID userId,
            @Valid @RequestBody UploadBatchRequest request) {
        List<UploadedItem> uploaded = uploadBatchUseCase.uploadBatch(new UploadBatchCommand(
                userId,
                new FilePath(request.path()),
                request.items().stream()
                        .map(item -> new UploadBatchCommand.Item(item.relativePath(), item.directory(), item.size()))
                        .toList(),
                request.resolutions()));
        return ApiResponse.success(UploadBatchResponse.from(uploaded));
    }
}
