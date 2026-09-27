package com.moduDrive.gateway.exception;

import com.moduDrive.common.core.exception.ExceptionCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthExceptionCase implements ExceptionCase {

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "허가받지 않은 사용자입니다."),
    NO_SESSION(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    // auth-service (or its Redis) couldn't answer — says nothing about the session itself, so it
    // must not look like a 401 that sends every user to the login screen (#447).
    AUTH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 로그인 상태를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;
}
