package com.moduDrive.storage.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.storage.adapter.in.web.dto.PresignBlockUploadsRequest;
import com.moduDrive.storage.adapter.in.web.dto.PresignBlockUploadsResponse;
import com.moduDrive.storage.application.port.in.command.PresignBlockUploadsCommand;
import com.moduDrive.storage.application.port.in.usecase.PresignBlockUploadsUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class PresignBlockUploadsController {

    private final PresignBlockUploadsUseCase presignBlockUploadsUseCase;

    @PostMapping("/api/v1/storage/blocks/upload-urls")
    public ApiResponse<PresignBlockUploadsResponse> presignBlockUploads(
            @RequestHeader("X_USER_ID") UUID userId,
            @Valid @RequestBody PresignBlockUploadsRequest request) {
        PresignBlockUploadsCommand command = new PresignBlockUploadsCommand(userId, request.blocks().stream()
                .map(block -> new PresignBlockUploadsCommand.Block(block.hash(), block.size()))
                .toList());
        return ApiResponse.success(new PresignBlockUploadsResponse(presignBlockUploadsUseCase.presignBlockUploads(command)));
    }
}
