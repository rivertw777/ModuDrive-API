package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileResponse;
import com.moduDrive.file.adapter.in.web.dto.UpdateFileFavoriteRequest;
import com.moduDrive.file.application.port.in.command.UpdateFileFavoriteCommand;
import com.moduDrive.file.application.port.in.usecase.UpdateFileFavoriteUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class UpdateFileFavoriteController {

    private final UpdateFileFavoriteUseCase updateFileFavoriteUseCase;

    @PatchMapping("/api/v1/files/{fileId}/favorite")
    public ApiResponse<FileResponse> updateFavorite(
            @PathVariable UUID fileId,
            @Valid @RequestBody UpdateFileFavoriteRequest request) {
        var command = new UpdateFileFavoriteCommand(fileId, request.favorite());
        return ApiResponse.success(FileResponse.from(updateFileFavoriteUseCase.updateFavorite(command)));
    }
}
