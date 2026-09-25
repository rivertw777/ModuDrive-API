package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.domain.vo.SessionId;
import com.moduDrive.common.api.dto.auth.SessionCookie;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * The session cookie (spec 004 1-1-2): HttpOnly, SameSite=Strict, Path=/, no Domain, and no
 * Max-Age — a browser-session cookie whose real lifetime is decided in Redis.
 */
@Component
class SessionCookieFactory {

    private final boolean secure;
    private final String cookieName;

    SessionCookieFactory(@Value("${session.cookie.secure}") boolean secure) {
        this.secure = secure;
        this.cookieName = SessionCookie.name(secure);
    }

    Optional<SessionId> readSessionId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .map(SessionId::new);
    }

    void setSessionId(HttpServletResponse response, SessionId sessionId) {
        response.addHeader(HttpHeaders.SET_COOKIE, baseCookie(sessionId.value()).build().toString());
    }

    void clearSessionId(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, baseCookie("").maxAge(Duration.ZERO).build().toString());
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/");
    }

}
