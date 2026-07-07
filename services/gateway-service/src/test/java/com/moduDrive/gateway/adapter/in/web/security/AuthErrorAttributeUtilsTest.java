package com.moduDrive.gateway.adapter.in.web.security;

import com.moduDrive.gateway.exception.AuthExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.util.function.Tuple2;

import static org.assertj.core.api.Assertions.assertThat;

class AuthErrorAttributeUtilsTest {

    @Nested
    @DisplayName("ExceptionCase로 에러 속성을 설정할 때")
    class WhenSettingByExceptionCase {

        @Test
        void setsStatusAndMessageFromExceptionCase() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/test").build());

            AuthErrorAttributeUtils.setAuthErrorAttribute(exchange, AuthExceptionCase.NO_AUTH_TOKEN);

            assertThat(exchange.getAttributes().get(AuthErrorAttributeUtils.STATUS_ATTRIBUTE))
                    .isEqualTo(HttpStatus.UNAUTHORIZED.name());
            assertThat(exchange.getAttributes().get(AuthErrorAttributeUtils.MESSAGE_ATTRIBUTE))
                    .isEqualTo(AuthExceptionCase.NO_AUTH_TOKEN.getMessage());
        }
    }

    @Nested
    @DisplayName("문자열로 에러 속성을 설정할 때")
    class WhenSettingByString {

        @Test
        void setsStatusAndMessageFromStrings() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/test").build());

            AuthErrorAttributeUtils.setAuthErrorAttribute(exchange, "CUSTOM_STATUS", "custom message");

            assertThat(exchange.getAttributes().get(AuthErrorAttributeUtils.STATUS_ATTRIBUTE))
                    .isEqualTo("CUSTOM_STATUS");
            assertThat(exchange.getAttributes().get(AuthErrorAttributeUtils.MESSAGE_ATTRIBUTE))
                    .isEqualTo("custom message");
        }
    }

    @Nested
    @DisplayName("에러 속성을 조회할 때")
    class WhenGettingAuthErrorAttribute {

        @Test
        void returnsSetAttributes() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/test").build());
            AuthErrorAttributeUtils.setAuthErrorAttribute(exchange, AuthExceptionCase.NO_AUTH_TOKEN);

            Tuple2<String, String> result = AuthErrorAttributeUtils.getAuthErrorAttribute(exchange);

            assertThat(result.getT1()).isEqualTo(HttpStatus.UNAUTHORIZED.name());
            assertThat(result.getT2()).isEqualTo(AuthExceptionCase.NO_AUTH_TOKEN.getMessage());
        }

        @Test
        void returnsDefaultsWhenNotSet() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/test").build());

            Tuple2<String, String> result = AuthErrorAttributeUtils.getAuthErrorAttribute(exchange);

            assertThat(result.getT1()).isEqualTo(AuthExceptionCase.UNAUTHORIZED.getHttpStatus().name());
            assertThat(result.getT2()).isEqualTo(AuthExceptionCase.UNAUTHORIZED.getMessage());
        }
    }
}
