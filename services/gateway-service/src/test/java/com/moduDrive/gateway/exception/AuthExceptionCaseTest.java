package com.moduDrive.gateway.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class AuthExceptionCaseTest {

    @Nested
    @DisplayName("UNAUTHORIZED 예외")
    class WhenUnauthorized {

        @Test
        void hasUnauthorizedHttpStatus() {
            assertThat(AuthExceptionCase.UNAUTHORIZED.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void hasMessage() {
            assertThat(AuthExceptionCase.UNAUTHORIZED.getMessage()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("NO_SESSION 예외")
    class WhenNoSession {

        @Test
        void hasUnauthorizedHttpStatus() {
            assertThat(AuthExceptionCase.NO_SESSION.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void hasMessage() {
            assertThat(AuthExceptionCase.NO_SESSION.getMessage()).isNotBlank();
        }
    }
}
