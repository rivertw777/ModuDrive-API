package com.moduDrive.file.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moduDrive.common.core.exception.ExceptionCase;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.usecase.CommitFileUploadUseCase;
import com.moduDrive.file.application.port.in.usecase.CommitFileUploadUseCase.CommitResult;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

/** One result per requested file and per requested folder, each in request order. */
public record CommitFileUploadResponse(List<Result> results, List<DirectoryResult> directories) {

    /** Exactly one of: {@code fileId} or {@code error}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DirectoryResult(UUID fileId, Error error) {}

    /** Exactly one of: {@code needBlocks} non-empty, {@code fileId}/{@code versionId} set, or
     * {@code error} — this file alone failed. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Result(List<String> needBlocks, UUID fileId, UUID versionId, Error error) {}

    /** Same shape as a failed {@code ApiResponse}: the HTTP status this file would have got alone. On a
     * 503, {@code retryAfter} is the seconds the response's own {@code Retry-After} would have said —
     * this response is a 200 and carries none (spec 001 2-4-3). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Error(int status, String message, Integer retryAfter) {}

    public static CommitFileUploadResponse from(List<CommitResult> results,
                                                List<CommitFileUploadUseCase.DirectoryResult> directories) {
        return new CommitFileUploadResponse(
                results.stream().map(r -> new Result(
                        r.needBlocks(),
                        r.version() == null ? null : r.version().getFileId(),
                        r.version() == null ? null : r.version().getId(),
                        error(r.error())
                )).toList(),
                directories.stream().map(r -> new DirectoryResult(r.fileId(), error(r.error()))).toList());
    }

    private static Error error(ExceptionCase error) {
        if (error == null) {
            return null;
        }
        Integer retryAfter = error.getHttpStatus() == HttpStatus.SERVICE_UNAVAILABLE
                ? Integer.valueOf(GlobalExceptionHandler.RETRY_AFTER_SECONDS) : null;
        return new Error(error.getHttpStatus().value(), error.getMessage(), retryAfter);
    }
}
