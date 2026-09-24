package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.ArchiveEntryResponse;
import com.moduDrive.file.adapter.in.web.dto.ResolveArchiveEntriesRequest;
import com.moduDrive.file.application.port.in.command.ResolveArchiveEntriesCommand;
import com.moduDrive.file.application.port.in.usecase.ResolveArchiveEntriesUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Service-to-service only: storage-service lays out a zip download through this route. Same
 * trust model as {@link GetLatestFileVersionsController} — {@code userId} is the original caller,
 * forwarded so the DOWNLOAD check still runs. POST only because a selection can be long. */
@WebAdapter
@RestController
@RequiredArgsConstructor
class ResolveArchiveEntriesController {

    private final ResolveArchiveEntriesUseCase resolveArchiveEntriesUseCase;

    @PostMapping("/internal/files/archive")
    public ApiResponse<List<ArchiveEntryResponse>> resolveArchiveEntries(
            @Valid @RequestBody ResolveArchiveEntriesRequest request) {
        List<ArchiveEntryResponse> entries = resolveArchiveEntriesUseCase
                .resolveArchiveEntries(new ResolveArchiveEntriesCommand(request.fileIds(), request.userId()))
                .stream()
                .map(ArchiveEntryResponse::from)
                .toList();
        return ApiResponse.success(entries);
    }
}
