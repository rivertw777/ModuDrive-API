package com.moduDrive.auth.exception;

import com.moduDrive.common.core.exception.ExceptionCase;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthExceptionCase implements ExceptionCase {

    MEMBER_NOT_VALID(HttpStatus.UNAUTHORIZED, "유효한 사용자가 아닙니다."),
    // One answer for missing, expired, logged-out and forged ids alike — no oracle to probe.
    SESSION_NOT_FOUND(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
