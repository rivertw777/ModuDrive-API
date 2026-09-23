package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.ArchiveEntryResponse;
import com.moduDrive.file.adapter.in.web.dto.ResolvePublicArchiveEntriesRequest;
import com.moduDrive.file.application.port.in.command.ResolvePublicArchiveEntriesCommand;
import com.moduDrive.file.application.port.in.usecase.ResolvePublicArchiveEntriesUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Anonymous counterpart of {@link ResolveArchiveEntriesController}, same relationship as
 * {@link GetPublicFileRevisionsController} has to {@link GetLatestFileVersionsController}. */
@WebAdapter
@RestController
@RequiredArgsConstructor
class ResolvePublicArchiveEntriesController {

    private final ResolvePublicArchiveEntriesUseCase resolvePublicArchiveEntriesUseCase;

    @PostMapping("/internal/files/public/archive")
    public ApiResponse<List<ArchiveEntryResponse>> resolvePublicArchiveEntries(
            @Valid @RequestBody ResolvePublicArchiveEntriesRequest request) {
        List<ArchiveEntryResponse> entries = resolvePublicArchiveEntriesUseCase
                .resolvePublicArchiveEntries(new ResolvePublicArchiveEntriesCommand(request.fileIds(), request.key()))
                .stream()
                .map(ArchiveEntryResponse::from)
                .toList();
        return ApiResponse.success(entries);
    }
}
