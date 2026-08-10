package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.domain.model.TokenPair.RefreshToken;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.common.core.exception.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
class RefreshTokenCookieFactory {

    static final String COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final Duration maxAge;
    private final boolean secure;

    RefreshTokenCookieFactory(@Value("${jwt.refreshToken.expiration}") long refreshTokenExpiration,
                              @Value("${jwt.refreshToken.cookie.secure}") boolean secure) {
        this.maxAge = Duration.ofMillis(refreshTokenExpiration);
        this.secure = secure;
    }

    static RefreshToken readRefreshToken(String refreshTokenCookie) {
        if (refreshTokenCookie == null || refreshTokenCookie.isBlank()) {
            throw new BusinessException(AuthExceptionCase.TOKEN_INVALID);
        }
        return new RefreshToken(refreshTokenCookie);
    }

    void setRefreshToken(HttpServletResponse response, String refreshToken) {
        addCookie(response, refreshToken, maxAge);
    }

    void clearRefreshToken(HttpServletResponse response) {
        addCookie(response, "", Duration.ZERO);
    }

    private void addCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                // SameSite=None은 명세상 Secure 쿠키에서만 유효하므로 secure와 함께 움직인다
                .sameSite(secure ? "None" : "Lax")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

}
