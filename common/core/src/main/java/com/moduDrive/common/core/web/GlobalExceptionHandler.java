package com.moduDrive.common.core.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.core.exception.BusinessException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ObjectMapper objectMapper;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException e) {
        ApiResponse<Object> response = ApiResponse.error(e.getExceptionCase());
        return ResponseEntity
                .status(response.getStatus())
                .body(response);
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValidException(BindingResult bindingResult) {
        String message = bindingResult.getAllErrors().get(0).getDefaultMessage();
        ApiResponse<Object> response = ApiResponse.error(HttpStatus.BAD_REQUEST, message);
        return ResponseEntity
                .status(response.getStatus())
                .body(response);
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiResponse<Object>> handleFeignException(FeignException feignException) throws JsonProcessingException {
        String responseJson = feignException.contentUTF8();

        if (responseJson == null || responseJson.isBlank()) {
            ApiResponse<Object> response = ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR, "외부 서비스 오류");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

        Map<String, String> responseMap = objectMapper.readValue(responseJson, Map.class);
        String errorMessage = responseMap.getOrDefault("message", "외부 서비스 오류");

        int statusCode = feignException.status() > 0 ? feignException.status() : 500;
        ApiResponse<Object> response = ApiResponse.error(HttpStatus.valueOf(statusCode), errorMessage);
        return ResponseEntity.status(statusCode).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        ApiResponse<Object> response = ApiResponse.error(HttpStatus.BAD_REQUEST, e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGlobalException(Exception e) {
        // Never echo e.getMessage() here — it can carry raw infra detail (DB constraint names,
        // connection failures, etc, CWE-209). Full detail goes to the log only.
        logger.error("Unhandled exception", e);
        ApiResponse<Object> response = ApiResponse.error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "서버 오류가 발생했습니다."
        );
        return ResponseEntity
                .status(response.getStatus())
                .body(response);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingRequestHeaderException(MissingRequestHeaderException e) {
        ApiResponse<Object> response = ApiResponse.error(
                HttpStatus.BAD_REQUEST,
                e.getMessage()
        );
        return ResponseEntity
                .status(response.getStatus())
                .body(response);
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingServletRequestParameterException(
            org.springframework.web.bind.MissingServletRequestParameterException e) {
        ApiResponse<Object> response = ApiResponse.error(HttpStatus.BAD_REQUEST, e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

}
