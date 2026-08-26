package com.moduDrive.notification.exception;

import com.moduDrive.common.core.exception.ExceptionCase;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum NotificationExceptionCase implements ExceptionCase {

    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
