package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.application.port.in.command.VerifyLoginCommand;
import com.moduDrive.auth.application.port.in.usecase.VerifyLoginUseCase;
import com.moduDrive.auth.domain.model.LoginResult;
import com.moduDrive.auth.domain.vo.DeviceId;
import com.moduDrive.auth.domain.vo.LoginChallengeId;
import com.moduDrive.auth.domain.vo.SessionId;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.common.api.auth.SessionCookie;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VerifyLoginController.class)
@Import({GlobalExceptionHandler.class, SessionCookieFactory.class})
class VerifyLoginControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private VerifyLoginUseCase verifyLoginUseCase;

    private static final String REQUEST_JSON = """
            {"code":"042917"}
            """;
    private static final Cookie CHALLENGE_COOKIE = new Cookie(SessionCookie.LOGIN_CHALLENGE_NAME, "challenge-id");

    @Nested
    @DisplayName("코드가 맞을 때")
    class WhenCodeMatches {

        @Test
        @DisplayName("세션·기기 쿠키를 내려주고 확인 쿠키는 지운다")
        void setsSessionAndDeviceCookiesAndClearsTheChallenge() throws Exception {
            given(verifyLoginUseCase.verifyLogin(any(VerifyLoginCommand.class)))
                    .willReturn(new LoginResult.SignedIn(new SessionId("new-session-id"), new DeviceId("device-id")));

            mockMvc.perform(post("/api/v1/auth/login/verify")
                            .cookie(CHALLENGE_COOKIE, new Cookie(SessionCookie.NAME, "old-session-id"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isOk())
                    .andExpect(cookie().value(SessionCookie.NAME, "new-session-id"))
                    .andExpect(cookie().value(SessionCookie.DEVICE_NAME, "device-id"))
                    .andExpect(cookie().maxAge(SessionCookie.LOGIN_CHALLENGE_NAME, 0));

            then(verifyLoginUseCase).should().verifyLogin(new VerifyLoginCommand(
                    new LoginChallengeId("challenge-id"), "042917", null, new SessionId("old-session-id")));
        }
    }

    @Nested
    @DisplayName("확인 쿠키가 없을 때")
    class WhenChallengeCookieIsMissing {

        @Test
        @DisplayName("시간이 지난 것과 같은 400으로 답한다")
        void returnsExpired() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(AuthExceptionCase.LOGIN_VERIFICATION_EXPIRED.getMessage()))
                    .andExpect(cookie().doesNotExist(SessionCookie.NAME));

            then(verifyLoginUseCase).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("코드가 틀렸을 때")
    class WhenCodeIsWrong {

        @Test
        void returnsBadRequestWithoutSessionCookie() throws Exception {
            willThrow(new BusinessException(AuthExceptionCase.INVALID_LOGIN_VERIFICATION_CODE))
                    .given(verifyLoginUseCase).verifyLogin(any(VerifyLoginCommand.class));

            mockMvc.perform(post("/api/v1/auth/login/verify")
                            .cookie(CHALLENGE_COOKIE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(AuthExceptionCase.INVALID_LOGIN_VERIFICATION_CODE.getMessage()))
                    .andExpect(cookie().doesNotExist(SessionCookie.NAME));
        }
    }

    @Nested
    @DisplayName("코드가 비어 있을 때")
    class WhenCodeIsBlank {

        @Test
        void returnsBadRequest() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login/verify")
                            .cookie(CHALLENGE_COOKIE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"\"}"))
                    .andExpect(status().isBadRequest());

            then(verifyLoginUseCase).shouldHaveNoInteractions();
        }
    }
}
