package com.moduDrive.auth.adapter.in.web.controller;

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
}
