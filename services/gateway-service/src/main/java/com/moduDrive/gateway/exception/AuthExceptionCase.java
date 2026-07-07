package com.moduDrive.gateway.exception;

import com.moduDrive.common.core.exception.ExceptionCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthExceptionCase implements ExceptionCase {

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "허가받지 않은 사용자입니다."),
    NO_AUTH_TOKEN(HttpStatus.UNAUTHORIZED, "인증 토큰이 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
