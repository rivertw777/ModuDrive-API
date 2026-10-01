package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.application.port.in.usecase.SendLoginCodeUseCase;
import com.moduDrive.auth.domain.vo.LoginChallengeId;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SendLoginCodeController.class)
@Import({GlobalExceptionHandler.class, SessionCookieFactory.class})
class SendLoginCodeControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private SendLoginCodeUseCase sendLoginCodeUseCase;

    private static final LoginChallengeId CHALLENGE_ID = new LoginChallengeId("challenge-id");
    private static final Cookie CHALLENGE_COOKIE = new Cookie(SessionCookie.LOGIN_CHALLENGE_NAME, CHALLENGE_ID.value());

    @Nested
    @DisplayName("확인 쿠키가 있을 때")
    class WhenChallengeCookieIsPresent {

        @Test
        @DisplayName("코드를 보내고 확인 쿠키의 5분을 새로 시작한다")
        void sendsTheCodeAndRestartsTheCookie() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login/code").cookie(CHALLENGE_COOKIE))
                    .andExpect(status().isOk())
                    .andExpect(cookie().value(SessionCookie.LOGIN_CHALLENGE_NAME, CHALLENGE_ID.value()))
                    .andExpect(cookie().maxAge(SessionCookie.LOGIN_CHALLENGE_NAME, 300));

            then(sendLoginCodeUseCase).should().sendLoginCode(CHALLENGE_ID);
        }

        @Test
        @DisplayName("발송 한도에 걸리면 429로 답한다")
        void returnsTooManyRequests() throws Exception {
            willThrow(new BusinessException(AuthExceptionCase.TOO_MANY_LOGIN_CODE_REQUESTS))
                    .given(sendLoginCodeUseCase).sendLoginCode(CHALLENGE_ID);

            mockMvc.perform(post("/api/v1/auth/login/code").cookie(CHALLENGE_COOKIE))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.message").value(AuthExceptionCase.TOO_MANY_LOGIN_CODE_REQUESTS.getMessage()));
        }
    }

    @Nested
    @DisplayName("확인 쿠키가 없을 때")
    class WhenChallengeCookieIsMissing {

        @Test
        @DisplayName("시간이 지난 것과 같은 400으로 답한다")
        void returnsExpired() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login/code"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(AuthExceptionCase.LOGIN_VERIFICATION_EXPIRED.getMessage()));

            then(sendLoginCodeUseCase).shouldHaveNoInteractions();
        }
    }
}
