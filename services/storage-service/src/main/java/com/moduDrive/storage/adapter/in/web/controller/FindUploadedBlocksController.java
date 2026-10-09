package com.moduDrive.storage.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.storage.adapter.in.web.dto.FindUploadedBlocksRequest;
import com.moduDrive.storage.application.port.in.usecase.FindUploadedBlocksUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Internal route for file-service's commit; the gateway doesn't expose it. */
@WebAdapter
@RestController
@RequiredArgsConstructor
class FindUploadedBlocksController {

    private final FindUploadedBlocksUseCase findUploadedBlocksUseCase;

    @PostMapping("/internal/storage/blocks/uploaded")
    public ApiResponse<Map<String, Integer>> findUploadedBlocks(@Valid @RequestBody FindUploadedBlocksRequest request) {
        return ApiResponse.success(findUploadedBlocksUseCase.findUploadedBlocks(request.ownerId(), request.hashes()));
    }
}
