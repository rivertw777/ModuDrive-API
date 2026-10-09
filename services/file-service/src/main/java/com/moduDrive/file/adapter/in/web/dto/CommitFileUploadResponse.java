package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.application.port.in.usecase.CommitFileUploadUseCase.CommitResult;

import java.util.List;
import java.util.UUID;

/** {@code needBlocks} empty means the version was made: {@code versionId} is set. */
public record CommitFileUploadResponse(List<String> needBlocks, UUID versionId) {

    public static CommitFileUploadResponse from(CommitResult result) {
        return new CommitFileUploadResponse(result.needBlocks(),
                result.version() == null ? null : result.version().getId());
    }
}
