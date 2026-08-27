package com.moduDrive.common.core.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new ObjectMapper());

    @Nested
    @DisplayName("처리되지 않은 예외가 발생했을 때")
    class WhenUnhandledExceptionOccurs {

        @Test
        void doesNotLeakTheRawExceptionMessage() {
            RuntimeException e = new RuntimeException(
                    "duplicate key value violates unique constraint \"uk_member_email\"");

            ResponseEntity<ApiResponse<Object>> response = handler.handleGlobalException(e);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().getMessage())
                    .isEqualTo("서버 오류가 발생했습니다.")
                    .doesNotContain("uk_member_email");
        }
    }

    @Nested
    @DisplayName("요청 본문을 파싱할 수 없을 때")
    class WhenRequestBodyIsUnreadable {

        @Test
        void returns400InsteadOf500() {
            HttpMessageNotReadableException e =
                    new HttpMessageNotReadableException("broken json", (HttpInputMessage) null);

            ResponseEntity<ApiResponse<Object>> response = handler.handleHttpMessageNotReadableException(e);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("경로/쿼리 파라미터 타입이 맞지 않을 때")
    class WhenParameterTypeMismatches {

        @Test
        void returns400InsteadOf500() {
            MethodArgumentTypeMismatchException e =
                    new MethodArgumentTypeMismatchException("abc", Integer.class, "chunkIndex", null, null);

            ResponseEntity<ApiResponse<Object>> response = handler.handleMethodArgumentTypeMismatchException(e);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getMessage()).contains("chunkIndex");
        }
    }
}
