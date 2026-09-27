package com.moduDrive.common.core.web;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.moduDrive.common.core.exception.ExceptionCase;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ApiResponse<T> {

    private HttpStatus status;
    private String message;
    private T data;

    ApiResponse() {}

    private ApiResponse(HttpStatus status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(HttpStatus.OK, null, data);
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(HttpStatus.OK, "success", null);
    }

    public static <T> ApiResponse<T> error(ExceptionCase exceptionCase) {
        return new ApiResponse<>(exceptionCase.getHttpStatus(), exceptionCase.getMessage(), null);
    }

    public static <T> ApiResponse<T> error(ExceptionCase exceptionCase, T data) {
        return new ApiResponse<>(exceptionCase.getHttpStatus(), exceptionCase.getMessage(), data);
    }

    public static <T> ApiResponse<T> error(HttpStatus httpStatus, String message) {
        return new ApiResponse<>(httpStatus, message, null);
    }

}
