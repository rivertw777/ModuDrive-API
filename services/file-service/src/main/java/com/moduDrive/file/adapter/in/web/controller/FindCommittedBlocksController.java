package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FindCommittedBlocksRequest;
import com.moduDrive.file.application.port.in.usecase.FindCommittedBlocksUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Internal route for storage-service's uncommitted-upload sweep; the gateway doesn't expose it. */
@WebAdapter
@RestController
@RequiredArgsConstructor
class FindCommittedBlocksController {

    private final FindCommittedBlocksUseCase findCommittedBlocksUseCase;

    @PostMapping("/internal/files/blocks/committed")
    public ApiResponse<List<String>> findCommittedBlocks(@Valid @RequestBody FindCommittedBlocksRequest request) {
        return ApiResponse.success(List.copyOf(
                findCommittedBlocksUseCase.findCommittedBlocks(request.ownerId(), request.hashes())));
    }
}
