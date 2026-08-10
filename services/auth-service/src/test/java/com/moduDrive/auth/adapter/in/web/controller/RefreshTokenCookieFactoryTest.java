package com.moduDrive.auth.adapter.in.web.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenCookieFactoryTest {

    private static final long REFRESH_TOKEN_EXPIRATION = 604800000L;

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private String setCookieHeader() {
        return response.getHeader(HttpHeaders.SET_COOKIE);
    }

    @Nested
    @DisplayName("secure 프로퍼티가 true일 때 (운영 기본값)")
    class WhenSecureIsEnabled {

        private final RefreshTokenCookieFactory factory =
                new RefreshTokenCookieFactory(REFRESH_TOKEN_EXPIRATION, true);

        @Test
        void issuesSecureCookieWithSameSiteNone() {
            factory.setRefreshToken(response, "refresh-token");

            assertThat(setCookieHeader())
                    .contains("refresh_token=refresh-token")
                    .contains("Secure")
                    .contains("SameSite=None")
                    .contains("HttpOnly")
                    .contains("Path=/api/v1/auth")
                    .contains("Max-Age=604800");
        }
    }

    @Nested
    @DisplayName("secure 프로퍼티가 false일 때 (dev 프로파일)")
    class WhenSecureIsDisabled {

        private final RefreshTokenCookieFactory factory =
                new RefreshTokenCookieFactory(REFRESH_TOKEN_EXPIRATION, false);

        @Test
        void fallsBackToSameSiteLaxWithoutSecureFlag() {
            factory.setRefreshToken(response, "refresh-token");

            assertThat(setCookieHeader())
                    .doesNotContain("Secure")
                    .contains("SameSite=Lax")
                    .contains("HttpOnly");
        }

        @Test
        void clearsCookieWithSameAttributes() {
            factory.clearRefreshToken(response);

            assertThat(setCookieHeader())
                    .contains("refresh_token=")
                    .contains("Max-Age=0")
                    .doesNotContain("Secure")
                    .contains("SameSite=Lax");
        }
    }
}
