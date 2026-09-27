package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.domain.vo.DeviceId;
import com.moduDrive.auth.domain.vo.LoginChallengeId;
import com.moduDrive.auth.domain.vo.SessionId;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCookieFactoryTest {

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private String setCookieHeader() {
        return response.getHeader(HttpHeaders.SET_COOKIE);
    }

    private final SessionCookieFactory factory = new SessionCookieFactory();

    @Nested
    @DisplayName("세션 쿠키를 내리고 읽을 때")
    class WhenHandlingTheSessionCookie {

        @Test
        @DisplayName("__Host- 접두어와 HttpOnly·Secure·SameSite=Strict·Path=/ 로, 만료 없이 내려준다")
        void issuesHostPrefixedBrowserSessionCookie() {
            factory.setSessionId(response, new SessionId("session-id"));

            assertThat(setCookieHeader())
                    .startsWith("__Host-session=session-id")
                    .contains("HttpOnly")
                    .contains("Secure")
                    .contains("SameSite=Strict")
                    .contains("Path=/")
                    .doesNotContain("Max-Age")
                    .doesNotContain("Expires")
                    .doesNotContain("Domain");
        }

        @Test
        void clearsCookieWithSameAttributes() {
            factory.clearSessionId(response);

            assertThat(setCookieHeader())
                    .startsWith("__Host-session=;")
                    .contains("Max-Age=0")
                    .contains("Secure")
                    .contains("Path=/");
        }

        @Test
        @DisplayName("접두어 없는 이름의 쿠키는 읽지 않는다")
        void readsOnlyTheHostPrefixedCookie() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new Cookie("session", "planted"), new Cookie("__Host-session", "real"));

            assertThat(factory.readSessionId(request)).contains(new SessionId("real"));
        }

        @Test
        void readsNothingWhenCookieIsAbsentOrBlank() {
            MockHttpServletRequest noCookies = new MockHttpServletRequest();
            MockHttpServletRequest blankCookie = new MockHttpServletRequest();
            blankCookie.setCookies(new Cookie("__Host-session", ""));

            assertThat(factory.readSessionId(noCookies)).isEmpty();
            assertThat(factory.readSessionId(blankCookie)).isEmpty();
        }
    }

    @Nested
    @DisplayName("새 기기 확인 쿠키들을 내리고 읽을 때")
    class WhenHandlingTheDeviceAndChallengeCookies {

        @Test
        @DisplayName("기기 쿠키는 세션 쿠키와 같은 속성에 1년 수명으로 내려준다")
        void issuesAYearLongDeviceCookie() {
            factory.setDeviceId(response, new DeviceId("device-id"));

            assertThat(setCookieHeader())
                    .startsWith("__Host-device=device-id")
                    .contains("Max-Age=31536000", "HttpOnly", "Secure", "SameSite=Strict", "Path=/")
                    .doesNotContain("Domain");
        }

        @Test
        @DisplayName("확인 쿠키는 5분 수명으로 내려주고, 지울 때는 수명 0으로 내려준다")
        void issuesAndClearsATenMinuteChallengeCookie() {
            factory.setLoginChallengeId(response, new LoginChallengeId("challenge-id"));
            factory.clearLoginChallengeId(response);

            assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).satisfiesExactly(
                    issued -> assertThat(issued).startsWith("__Host-login-challenge=challenge-id")
                            .contains("Max-Age=300", "HttpOnly", "Secure", "SameSite=Strict"),
                    cleared -> assertThat(cleared).startsWith("__Host-login-challenge=;").contains("Max-Age=0"));
        }

        @Test
        void readsEachCookieByItsOwnName() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new Cookie("__Host-device", "device-id"), new Cookie("__Host-login-challenge", "challenge-id"));

            assertThat(factory.readDeviceId(request)).contains(new DeviceId("device-id"));
            assertThat(factory.readLoginChallengeId(request)).contains(new LoginChallengeId("challenge-id"));
            assertThat(factory.readSessionId(request)).isEmpty();
        }
    }
}
