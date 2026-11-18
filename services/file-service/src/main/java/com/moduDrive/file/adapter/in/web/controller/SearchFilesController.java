package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileResponse;
import com.moduDrive.file.application.port.in.command.SearchFilesCommand;
import com.moduDrive.file.application.port.in.usecase.SearchFilesUseCase;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.exception.FileExceptionCase;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@WebAdapter
@RestController
class SearchFilesController {

    private final SearchFilesUseCase searchFilesUseCase;
    private final Bulkhead bulkhead;

    SearchFilesController(SearchFilesUseCase searchFilesUseCase, BulkheadRegistry bulkheadRegistry) {
        this.searchFilesUseCase = searchFilesUseCase;
        this.bulkhead = bulkheadRegistry.bulkhead("fileSearch");
    }

    /** Spec 006 2-5-3: the seat is taken before the transaction, so searches past it never take a pooled
     * connection — the pool stays for everything else. */
    @GetMapping("/api/v1/files/search")
    public ApiResponse<List<FileResponse>> searchFiles(
            @RequestHeader("X_USER_ID") UUID userId,
            @RequestParam String query) {
        List<File> found;
        try {
            found = bulkhead.executeSupplier(() -> searchFilesUseCase.searchFiles(new SearchFilesCommand(userId, query)));
        } catch (BulkheadFullException e) {
            throw new BusinessException(FileExceptionCase.SEARCH_UNAVAILABLE);
        }
        return ApiResponse.success(found.stream().map(FileResponse::from).toList());
    }
}
