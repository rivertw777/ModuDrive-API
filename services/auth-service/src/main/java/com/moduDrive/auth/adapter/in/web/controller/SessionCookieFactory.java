package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.domain.vo.DeviceId;
import com.moduDrive.auth.domain.vo.LoginChallengeId;
import com.moduDrive.auth.domain.vo.SessionId;
import com.moduDrive.common.api.auth.SessionCookie;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * The auth cookies (spec 004 1-1-2, 2-2), all HttpOnly, Secure, SameSite=Strict, Path=/, no Domain.
 * The session cookie has no Max-Age — a browser-session cookie whose real lifetime is decided in
 * Redis. The device cookie lasts a year, restarted on every sign-in; the login-challenge cookie lasts
 * as long as its challenge.
 */
@Component
class SessionCookieFactory {

    private static final Duration DEVICE_MAX_AGE = Duration.ofDays(365);
    private static final Duration LOGIN_CHALLENGE_MAX_AGE = Duration.ofMinutes(5);

    Optional<SessionId> readSessionId(HttpServletRequest request) {
        return read(request, SessionCookie.NAME).map(SessionId::new);
    }

    Optional<DeviceId> readDeviceId(HttpServletRequest request) {
        return read(request, SessionCookie.DEVICE_NAME).map(DeviceId::new);
    }

    Optional<LoginChallengeId> readLoginChallengeId(HttpServletRequest request) {
        return read(request, SessionCookie.LOGIN_CHALLENGE_NAME).map(LoginChallengeId::new);
    }

    void setSessionId(HttpServletResponse response, SessionId sessionId) {
        add(response, baseCookie(SessionCookie.NAME, sessionId.value()));
    }

    void setDeviceId(HttpServletResponse response, DeviceId deviceId) {
        add(response, baseCookie(SessionCookie.DEVICE_NAME, deviceId.value()).maxAge(DEVICE_MAX_AGE));
    }

    void setLoginChallengeId(HttpServletResponse response, LoginChallengeId challengeId) {
        add(response, baseCookie(SessionCookie.LOGIN_CHALLENGE_NAME, challengeId.value()).maxAge(LOGIN_CHALLENGE_MAX_AGE));
    }

    void clearSessionId(HttpServletResponse response) {
        add(response, baseCookie(SessionCookie.NAME, "").maxAge(Duration.ZERO));
    }

    void clearLoginChallengeId(HttpServletResponse response) {
        add(response, baseCookie(SessionCookie.LOGIN_CHALLENGE_NAME, "").maxAge(Duration.ZERO));
    }

    private static Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private static void add(HttpServletResponse response, ResponseCookie.ResponseCookieBuilder cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());
    }

    private static ResponseCookie.ResponseCookieBuilder baseCookie(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/");
    }

}
