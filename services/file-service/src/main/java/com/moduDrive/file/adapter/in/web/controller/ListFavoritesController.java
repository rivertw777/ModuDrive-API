package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileResponse;
import com.moduDrive.file.application.port.in.command.ListFavoritesCommand;
import com.moduDrive.file.application.port.in.usecase.ListFavoritesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class ListFavoritesController {

    private final ListFavoritesUseCase listFavoritesUseCase;

    @GetMapping("/api/v1/files/favorites")
    public ApiResponse<List<FileResponse>> listFavorites(@RequestHeader("X_USER_ID") UUID userId) {
        List<FileResponse> files = listFavoritesUseCase.listFavorites(new ListFavoritesCommand(userId))
                .stream()
                .map(FileResponse::from)
                .toList();
        return ApiResponse.success(files);
    }
}
