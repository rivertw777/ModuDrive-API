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
    // New-device login (spec 004 2-1): no waiting login (never started, or 5 min since the login or the last code).
    LOGIN_VERIFICATION_EXPIRED(HttpStatus.BAD_REQUEST, "인증 시간이 지났습니다. 다시 로그인해 주세요."),
    INVALID_LOGIN_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 일치하지 않습니다."),
    // Same answers as member-service's sign-up email check.
    LOGIN_VERIFICATION_ATTEMPTS_EXCEEDED(HttpStatus.GONE, "인증 코드 입력 횟수를 초과했습니다. 코드를 다시 받아 주세요."),
    LOGIN_CODE_REQUEST_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 빈번합니다. 잠시 후 다시 시도해 주세요."),
    TOO_MANY_LOGIN_CODE_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요."),
    // One answer for missing, expired, logged-out and forged ids alike — no oracle to probe.
    SESSION_NOT_FOUND(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
