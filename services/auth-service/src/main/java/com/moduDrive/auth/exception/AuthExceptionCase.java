package com.moduDrive.auth.exception;

import com.moduDrive.common.core.exception.ExceptionCase;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthExceptionCase implements ExceptionCase {

    MEMBER_NOT_VALID(HttpStatus.UNAUTHORIZED, "유효한 사용자가 아닙니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "토큰 형식이 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
