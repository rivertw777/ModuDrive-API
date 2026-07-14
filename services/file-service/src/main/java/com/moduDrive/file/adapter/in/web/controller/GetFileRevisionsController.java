package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileVersionResponse;
import com.moduDrive.file.application.port.in.command.GetFileRevisionsCommand;
import com.moduDrive.file.application.port.in.usecase.GetFileRevisionsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class GetFileRevisionsController {

    private final GetFileRevisionsUseCase getFileRevisionsUseCase;

    @GetMapping("/api/v1/files/{fileId}/revisions")
    public ApiResponse<List<FileVersionResponse>> getFileRevisions(
            @PathVariable UUID fileId,
            @RequestParam(defaultValue = "20") int limit) {
        List<FileVersionResponse> revisions = getFileRevisionsUseCase
                .getFileRevisions(new GetFileRevisionsCommand(fileId, limit))
                .stream()
                .map(FileVersionResponse::from)
                .toList();
        return ApiResponse.success(revisions);
    }
}
