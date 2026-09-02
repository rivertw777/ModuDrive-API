package com.moduDrive.storage.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.storage.application.port.in.command.PurgeStoredFileCommand;
import com.moduDrive.storage.application.port.in.usecase.PurgeStoredFileUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Service-to-service only, same reasoning as file-service's GetLatestFileVersionsController:
 * kept off {@code /api/v1/storage/**} (the gateway only proxies that prefix) so the caller here
 * is file-service purging a trashed file's blocks, not an end user directly. userId is the
 * file's owner (file-service already verified ownership before calling this), forwarded so
 * GetFileVersionPort's revision lookup can pass file-service's own access check. */
@WebAdapter
@RestController
@RequiredArgsConstructor
class PurgeStoredFileController {

    private final PurgeStoredFileUseCase purgeStoredFileUseCase;

    @DeleteMapping("/internal/storage/{fileId}")
    public ApiResponse<Void> purgeStoredFile(@PathVariable UUID fileId, @RequestParam UUID userId) {
        purgeStoredFileUseCase.purgeStoredFile(new PurgeStoredFileCommand(fileId, userId));
        return ApiResponse.success();
    }
}
