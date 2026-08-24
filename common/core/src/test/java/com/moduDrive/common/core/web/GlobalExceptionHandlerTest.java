package com.moduDrive.common.core.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
}
