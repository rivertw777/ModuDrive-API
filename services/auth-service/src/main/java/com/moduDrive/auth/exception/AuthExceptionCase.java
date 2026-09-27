package com.moduDrive.auth.exception;

import com.moduDrive.common.core.exception.ExceptionCase;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthExceptionCase implements ExceptionCase {

    MEMBER_NOT_VALID(HttpStatus.UNAUTHORIZED, "유효한 사용자가 아닙니다."),
    // Same wording as member-service's answer, whichever of email or password was wrong (#445).
    INVALID_CREDENTIALS(HttpStatus.BAD_REQUEST, "이메일 또는 비밀번호가 일치하지 않습니다."),
    TOO_MANY_LOGIN_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    // One answer for missing, expired, logged-out and forged ids alike — no oracle to probe.
    SESSION_NOT_FOUND(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    // Only a browser holding a once-real session id can get this, so it tells an outsider nothing.
    SESSION_REPLACED(HttpStatus.UNAUTHORIZED, "다른 곳에서 로그인되어 로그아웃되었습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
