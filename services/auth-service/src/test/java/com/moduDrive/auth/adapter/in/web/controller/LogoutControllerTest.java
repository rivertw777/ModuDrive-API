package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.application.port.in.command.LogoutCommand;
import com.moduDrive.auth.application.port.in.usecase.LogoutUseCase;
import com.moduDrive.auth.domain.vo.SessionId;
import com.moduDrive.common.api.dto.auth.SessionCookie;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LogoutController.class)
@Import({GlobalExceptionHandler.class, SessionCookieFactory.class})
class LogoutControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private LogoutUseCase logoutUseCase;

    private static final String COOKIE_NAME = SessionCookie.SECURE_NAME;

    @Nested
    @DisplayName("세션 쿠키로 로그아웃을 요청할 때")
    class WhenSessionCookieIsPresent {

        @Test
        void deletesSessionAndClearsCookie() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(COOKIE_NAME, "session-id")))
                    .andExpect(status().isOk())
                    .andExpect(cookie().value(COOKIE_NAME, ""))
                    .andExpect(cookie().maxAge(COOKIE_NAME, 0))
                    .andExpect(cookie().path(COOKIE_NAME, "/"));

            then(logoutUseCase).should().logout(new LogoutCommand(new SessionId("session-id")));
        }
    }

    @Nested
    @DisplayName("세션 쿠키가 없을 때")
    class WhenSessionCookieIsAbsent {

        @Test
        @DisplayName("실패하지 않고 쿠키만 지운다")
        void stillSucceedsAndClearsCookie() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(cookie().maxAge(COOKIE_NAME, 0));

            then(logoutUseCase).shouldHaveNoInteractions();
        }
    }
}
