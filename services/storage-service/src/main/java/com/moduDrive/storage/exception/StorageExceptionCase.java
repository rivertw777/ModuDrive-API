package com.moduDrive.storage.exception;

import com.moduDrive.common.core.exception.ExceptionCase;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum StorageExceptionCase implements ExceptionCase {

    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "업로드 세션을 찾을 수 없습니다."),
    SESSION_OWNER_MISMATCH(HttpStatus.FORBIDDEN, "해당 업로드 세션에 접근할 권한이 없습니다."),
    SESSION_ALREADY_COMPLETED(HttpStatus.BAD_REQUEST, "이미 완료된 업로드 세션입니다."),
    CHUNKS_INCOMPLETE(HttpStatus.BAD_REQUEST, "아직 모든 청크가 업로드되지 않았습니다."),
    CHUNK_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "청크 업로드에 실패했습니다."),
    FILE_NOT_FOUND_IN_STORAGE(HttpStatus.NOT_FOUND, "스토리지에서 파일을 찾을 수 없습니다."),
    STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "스토리지 오류가 발생했습니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일 크기는 5GB를 초과할 수 없습니다."),
    /** Same message whether the credential is missing, expired, or was issued for a different
     * file — an anonymous caller must not be able to tell those apart. */
    UNAUTHENTICATED_VIEW_REQUEST(HttpStatus.UNAUTHORIZED, "인증되지 않은 요청입니다."),
    /** Guards inline preview only — regular download has no such cap. Without it, previewing a
     * multi-GB file would fully materialize it in heap (twice: once assembled, once sliced for
     * Range) on a route the gateway now permits without auth. */
    PREVIEW_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "미리보기는 100MB를 초과하는 파일을 지원하지 않습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
