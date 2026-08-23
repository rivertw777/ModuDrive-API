package com.moduDrive.file.exception;

import com.moduDrive.common.core.exception.ExceptionCase;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum FileExceptionCase implements ExceptionCase {

    NAMESPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "네임스페이스를 찾을 수 없습니다."),
    NAMESPACE_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 존재하는 네임스페이스입니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),
    FILE_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "이미 삭제된 파일입니다."),
    FILE_NOT_DELETED(HttpStatus.BAD_REQUEST, "삭제된 파일이 아닙니다."),
    FILE_NOT_UPLOADED(HttpStatus.BAD_REQUEST, "업로드 완료되지 않은 파일입니다."),
    DIRECTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "디렉토리를 찾을 수 없습니다."),
    FILE_SHARE_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 공유된 파일입니다."),
    FILE_SHARE_SELF_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "자기 자신에게는 공유할 수 없습니다."),
    FILE_SHARE_NOT_FOUND(HttpStatus.NOT_FOUND, "공유 정보를 찾을 수 없습니다."),
    FILE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "파일에 접근할 권한이 없습니다."),
    INVALID_LINK_ROLE(HttpStatus.BAD_REQUEST, "링크 공유 역할은 필수입니다."),
    SHARE_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 이메일의 회원을 찾을 수 없습니다."),
    INVALID_MOVE_TARGET(HttpStatus.BAD_REQUEST, "디렉토리를 자기 자신의 하위 경로로 이동할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
