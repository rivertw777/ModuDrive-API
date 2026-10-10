package com.moduDrive.storage.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.storage.application.port.in.command.UploadBlocksCommand;
import com.moduDrive.storage.application.port.in.usecase.UploadBlocksUseCase;
import com.moduDrive.storage.exception.StorageExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class UploadBlocksController {

    private final UploadBlocksUseCase uploadBlocksUseCase;

    /** Multipart: {@code hash} and {@code block} repeated, paired by position. */
    @PostMapping("/api/v1/storage/blocks")
    public ApiResponse<Void> uploadBlocks(
            @RequestHeader("X_USER_ID") UUID userId,
            @RequestParam("hash") List<String> hashes,
            @RequestParam("block") List<MultipartFile> parts) throws IOException {
        if (hashes.size() != parts.size()) {
            throw new BusinessException(StorageExceptionCase.INVALID_BLOCK);
        }
        List<UploadBlocksCommand.Block> blocks = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) {
            blocks.add(new UploadBlocksCommand.Block(hashes.get(i), parts.get(i).getBytes()));
        }
        uploadBlocksUseCase.uploadBlocks(new UploadBlocksCommand(userId, blocks));
        return ApiResponse.success();
    }
}
