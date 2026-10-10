package com.moduDrive.storage.exception;

import com.moduDrive.common.core.exception.ExceptionCase;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum StorageExceptionCase implements ExceptionCase {

    /** Empty, or the bytes don't hash to the hash in the path. */
    INVALID_BLOCK(HttpStatus.BAD_REQUEST, "블록 내용이 해시와 일치하지 않습니다."),
    BLOCK_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "블록 하나는 블록 크기를 초과할 수 없습니다."),
    /** More than 64 blocks or 8MB in one upload request (spec 001 2장 4번). */
    BLOCK_BATCH_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "한 번에 보낼 수 있는 블록 양을 초과했습니다."),
    /** The owner already uploaded {@code storage.upload-blocks-per-window} blocks in the last 24h. */
    UPLOAD_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "24시간 업로드 한도(100GB)를 초과했습니다. 나중에 다시 시도해 주세요."),
    FILE_NOT_FOUND_IN_STORAGE(HttpStatus.NOT_FOUND, "스토리지에서 파일을 찾을 수 없습니다."),
    STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "스토리지 오류가 발생했습니다."),
    /** S3's circuit is open or its bulkhead is full — answered at once, without waiting on S3
     * (spec 006 2-4-5). */
    STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "저장소에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    TOO_MANY_BLOCKS(HttpStatus.BAD_REQUEST, "블록 수가 허용 범위를 초과했습니다."),
    /** Guards inline preview only — regular download has no such cap. Without it, previewing a
     * multi-GB file would fully materialize it in heap (twice: once assembled, once sliced for
     * Range) on a route the gateway now permits without auth. */
    PREVIEW_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "미리보기는 100MB를 초과하는 파일을 지원하지 않습니다."),
    /** A single file served more than its per-file daily volume — blocked until the ~24h window
     * rolls over, the same way Google Drive locks an over-downloaded shared file. */
    DOWNLOAD_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "이 파일의 다운로드 한도를 초과했습니다. 잠시 후 다시 시도해 주세요."),
    /** Missing, already used, or expired — the zip link is single-use and short-lived. */
    ARCHIVE_TOKEN_INVALID(HttpStatus.NOT_FOUND, "다운로드 링크가 만료되었습니다. 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;
}
