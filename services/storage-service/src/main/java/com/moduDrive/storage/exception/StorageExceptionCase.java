package com.moduDrive.storage.exception;

import com.moduDrive.common.core.exception.ExceptionCase;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum StorageExceptionCase implements ExceptionCase {

    /** No blocks or over 64 in one URL request, a malformed hash, or an empty block (spec 001 2-4-2). */
    INVALID_BLOCK(HttpStatus.BAD_REQUEST, "올바르지 않은 블록입니다."),
    BLOCK_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "블록 하나는 블록 크기를 초과할 수 없습니다."),
    /** The owner already got {@code storage.upload-blocks-per-window} upload URLs in the last 24h. */
    UPLOAD_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "24시간 업로드 한도를 초과했습니다. 나중에 다시 시도해 주세요."),
    FILE_NOT_FOUND_IN_STORAGE(HttpStatus.NOT_FOUND, "스토리지에서 파일을 찾을 수 없습니다."),
    STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "스토리지 오류가 발생했습니다."),
    /** S3's circuit is open or its bulkhead is full — answered at once, without waiting on S3
     * (spec 006 2-4-5). */
    STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "저장소에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    TOO_MANY_BLOCKS(HttpStatus.BAD_REQUEST, "블록 수가 허용 범위를 초과했습니다."),
    /** Inline preview only (regular download has no cap): a preview without a Range is assembled in heap. */
    PREVIEW_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "미리보기는 100MB를 초과하는 파일을 지원하지 않습니다."),
    /** The file served more than its per-file volume in the window (Google Drive style). */
    DOWNLOAD_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "이 파일의 다운로드 한도를 초과했습니다. 잠시 후 다시 시도해 주세요."),
    /** Missing, already used, or expired — the zip link is single-use and short-lived. */
    ARCHIVE_TOKEN_INVALID(HttpStatus.NOT_FOUND, "다운로드 링크가 만료되었습니다. 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;
}
