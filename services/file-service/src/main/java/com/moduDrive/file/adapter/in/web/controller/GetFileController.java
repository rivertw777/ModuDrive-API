package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.adapter.in.web.dto.FileResponse;
import com.moduDrive.file.application.port.in.command.GetFileCommand;
import com.moduDrive.file.application.port.in.command.RecordFileAccessCommand;
import com.moduDrive.file.application.port.in.usecase.GetFileUseCase;
import com.moduDrive.file.application.port.in.usecase.RecordFileAccessUseCase;
import com.moduDrive.file.domain.model.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@WebAdapter
@RestController
@RequiredArgsConstructor
class GetFileController {

    private final GetFileUseCase getFileUseCase;
    private final RecordFileAccessUseCase recordFileAccessUseCase;

    @GetMapping("/api/v1/files/{fileId}")
    public ApiResponse<FileResponse> getFile(
            @RequestHeader("X_USER_ID") UUID userId,
            @PathVariable UUID fileId) {
        File file = getFileUseCase.getFile(new GetFileCommand(fileId, userId));
        // Belt-and-suspenders alongside RecordFileAccessService's own catch: this call crosses
        // a @Transactional proxy boundary, so a failure could in principle surface here at
        // commit time rather than inside that method's try block. Either way, a tracking
        // failure must never turn an already-successful read into a 500.
        try {
            recordFileAccessUseCase.recordAccess(new RecordFileAccessCommand(userId, fileId));
        } catch (RuntimeException e) {
            log.warn("Failed to record file access for user={} file={}", userId, fileId, e);
        }
        return ApiResponse.success(FileResponse.from(file));
    }
}
