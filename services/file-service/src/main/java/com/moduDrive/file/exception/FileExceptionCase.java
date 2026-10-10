package com.moduDrive.file.exception;

import com.moduDrive.common.core.exception.ExceptionCase;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum FileExceptionCase implements ExceptionCase {

    NAMESPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "네임스페이스를 찾을 수 없습니다."),
    ARCHIVE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "한 번에 압축해 받을 수 있는 양은 파일 10,000개, 20GB까지입니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),
    FILE_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "이미 휴지통에 있는 항목입니다."),
    FILE_NOT_DELETED(HttpStatus.BAD_REQUEST, "휴지통에 있는 항목이 아닙니다."),
    FILE_NOT_UPLOADED(HttpStatus.BAD_REQUEST, "업로드 완료되지 않은 파일입니다."),
    FILE_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "같은 위치에 같은 이름의 항목이 이미 존재합니다."),
    DIRECTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "디렉토리를 찾을 수 없습니다."),
    FILE_SHARE_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 공유된 파일입니다."),
    FILE_SHARE_SELF_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "자기 자신에게는 공유할 수 없습니다."),
    FILE_SHARE_NOT_FOUND(HttpStatus.NOT_FOUND, "공유 정보를 찾을 수 없습니다."),
    FILE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "이 항목에 접근할 권한이 없습니다."),
    INVALID_LINK_ROLE(HttpStatus.BAD_REQUEST, "링크 공유는 뷰어 권한만 가능합니다."),
    SHARE_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 이메일의 회원을 찾을 수 없습니다."),
    INVALID_MOVE_TARGET(HttpStatus.BAD_REQUEST, "디렉토리를 자기 자신의 하위 경로로 이동할 수 없습니다."),
    FILE_BATCH_CONFLICT(HttpStatus.CONFLICT, "같은 이름의 항목이 이미 있습니다."),
    INVALID_BATCH_ITEM(HttpStatus.BAD_REQUEST, "업로드 항목의 경로나 크기가 올바르지 않습니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일 크기는 5GB를 초과할 수 없습니다."),
    /** The blocklist doesn't fit the declared size, or the uploadId belongs to another file. */
    INVALID_BLOCKLIST(HttpStatus.BAD_REQUEST, "업로드한 파일 정보가 올바르지 않습니다."),
    /** One commit request's blocklists add up to more than 1,280 hashes (spec 001 2장). */
    COMMIT_TOO_LARGE(HttpStatus.BAD_REQUEST, "한 번에 확정할 수 있는 블록 수를 초과했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
