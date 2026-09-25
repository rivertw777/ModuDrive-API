package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.application.port.in.command.LoginCommand;
import com.moduDrive.auth.application.port.in.usecase.LoginUseCase;
import com.moduDrive.auth.domain.vo.SessionId;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.api.dto.auth.SessionCookie;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoginController.class)
@Import({GlobalExceptionHandler.class, SessionCookieFactory.class})
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private LoginUseCase loginUseCase;

    private static final String COOKIE_NAME = SessionCookie.SECURE_NAME;
    private static final String REQUEST_JSON = """
            {"email":"river@modudrive.com","password":"raw-password"}
            """;

    @Nested
    @DisplayName("유효한 로그인 요청일 때")
    class WhenRequestIsValid {

        @Test
        @DisplayName("세션 ID는 쿠키로만 내려주고 본문에는 싣지 않는다")
        void setsSessionCookieWithoutCredentialInBody() throws Exception {
            given(loginUseCase.login(any(LoginCommand.class))).willReturn(new SessionId("new-session-id"));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(cookie().value(COOKIE_NAME, "new-session-id"))
                    .andExpect(cookie().httpOnly(COOKIE_NAME, true))
                    .andExpect(cookie().secure(COOKIE_NAME, true))
                    .andExpect(cookie().sameSite(COOKIE_NAME, "Strict"))
                    .andExpect(cookie().path(COOKIE_NAME, "/"));
        }

        @Test
        void passesExistingSessionCookieAsPreviousSession() throws Exception {
            given(loginUseCase.login(any(LoginCommand.class))).willReturn(new SessionId("new-session-id"));

            mockMvc.perform(post("/api/v1/auth/login")
                            .cookie(new Cookie(COOKIE_NAME, "old-session-id"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isOk());

            ArgumentCaptor<LoginCommand> captor = ArgumentCaptor.forClass(LoginCommand.class);
            then(loginUseCase).should().login(captor.capture());
            assertThat(captor.getValue().getPreviousSessionId()).isEqualTo(new SessionId("old-session-id"));
            assertThat(captor.getValue().getMemberEmail().value()).isEqualTo("river@modudrive.com");
        }
    }

    @Nested
    @DisplayName("요청 값 검증에 실패했을 때")
    class WhenRequestIsInvalid {

        @Test
        void returnsBadRequest() throws Exception {
            String invalidJson = """
                    {"email":"not-an-email","password":"short"}
                    """;

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("유효하지 않은 사용자일 때")
    class WhenMemberIsNotValid {

        @Test
        void returnsUnauthorizedWithoutCookie() throws Exception {
            willThrow(new BusinessException(AuthExceptionCase.MEMBER_NOT_VALID))
                    .given(loginUseCase).login(any(LoginCommand.class));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(AuthExceptionCase.MEMBER_NOT_VALID.getMessage()))
                    .andExpect(cookie().doesNotExist(COOKIE_NAME));
        }
    }
}
