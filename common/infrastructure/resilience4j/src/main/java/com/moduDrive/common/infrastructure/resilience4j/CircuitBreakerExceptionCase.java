package com.moduDrive.common.infrastructure.resilience4j;

import com.moduDrive.common.core.exception.ExceptionCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CircuitBreakerExceptionCase implements ExceptionCase {

    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "서비스가 연결 불가능합니다. 잠시 후 다시 시도해주세요."),
    CONNECTION_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "서비스 요청 시간이 초과되었습니다. 잠시 후 다시 시도해주세요."),
    SERVICE_IS_OPEN(HttpStatus.SERVICE_UNAVAILABLE, "서비스가 일시적으로 차단되었습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus httpStatus;
    private final String message;
}
