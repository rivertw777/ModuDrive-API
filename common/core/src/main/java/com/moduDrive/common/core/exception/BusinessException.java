package com.moduDrive.common.core.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ExceptionCase exceptionCase;
    /** Optional extra body for the error response (e.g. FileAccessGuard attaches isDirectory to
     * FILE_ACCESS_DENIED); null otherwise. */
    private final Object data;

    public BusinessException(ExceptionCase exceptionCase) {
        this(exceptionCase, null);
    }

    public BusinessException(ExceptionCase exceptionCase, Object data) {
        super(exceptionCase.getMessage());
        this.exceptionCase = exceptionCase;
        this.data = data;
    }

}