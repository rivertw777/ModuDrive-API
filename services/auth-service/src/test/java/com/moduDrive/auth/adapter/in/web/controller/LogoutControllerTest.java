package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.application.port.in.command.LogoutCommand;
import com.moduDrive.auth.application.port.in.usecase.LogoutUseCase;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LogoutController.class)
@Import({GlobalExceptionHandler.class, RefreshTokenCookieFactory.class})
@TestPropertySource(properties = {
        "jwt.refreshToken.expiration=604800000",
        "jwt.refreshToken.cookie.secure=true"
})
class LogoutControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private LogoutUseCase logoutUseCase;

    private static final Cookie REFRESH_TOKEN_COOKIE = new Cookie("refresh_token", "refresh-token");

    @Nested
    @DisplayName("Authorization 헤더 없이 로그아웃을 요청할 때")
    class WhenAuthorizationHeaderIsAbsent {

        @Test
        void passesCookieRefreshTokenWithoutAccessToken() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout").cookie(REFRESH_TOKEN_COOKIE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("success"));

            ArgumentCaptor<LogoutCommand> captor = ArgumentCaptor.forClass(LogoutCommand.class);
            then(logoutUseCase).should().logout(captor.capture());
            assertThat(captor.getValue().getAccessToken()).isNull();
            assertThat(captor.getValue().getRefreshToken().getTokenValue()).isEqualTo("refresh-token");
        }

        @Test
        void clearsRefreshTokenCookie() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout").cookie(REFRESH_TOKEN_COOKIE))
                    .andExpect(status().isOk())
                    .andExpect(cookie().value("refresh_token", ""))
                    .andExpect(cookie().maxAge("refresh_token", 0))
                    .andExpect(cookie().httpOnly("refresh_token", true))
                    .andExpect(cookie().path("refresh_token", "/api/v1/auth"));
        }
    }

    @Nested
    @DisplayName("Bearer 액세스 토큰과 함께 로그아웃을 요청할 때")
    class WhenAuthorizationHeaderIsPresent {

        @Test
        void passesStrippedAccessTokenToUseCase() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .cookie(REFRESH_TOKEN_COOKIE)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("success"));

            ArgumentCaptor<LogoutCommand> captor = ArgumentCaptor.forClass(LogoutCommand.class);
            then(logoutUseCase).should().logout(captor.capture());
            assertThat(captor.getValue().getAccessToken().getTokenValue()).isEqualTo("access-token");
        }
    }

    @Nested
    @DisplayName("Authorization 헤더가 Bearer 형식이 아닐 때")
    class WhenAuthorizationHeaderIsNotBearer {

        @Test
        void treatsAccessTokenAsAbsent() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .cookie(REFRESH_TOKEN_COOKIE)
                            .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                    .andExpect(status().isOk());

            ArgumentCaptor<LogoutCommand> captor = ArgumentCaptor.forClass(LogoutCommand.class);
            then(logoutUseCase).should().logout(captor.capture());
            assertThat(captor.getValue().getAccessToken()).isNull();
        }
    }

    @Nested
    @DisplayName("리프레시 토큰 쿠키가 없을 때")
    class WhenRefreshTokenCookieIsAbsent {

        @Test
        void returnsUnauthorizedWithoutCallingUseCase() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(AuthExceptionCase.TOKEN_INVALID.getMessage()));

            then(logoutUseCase).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("리프레시 토큰이 유효하지 않을 때")
    class WhenRefreshTokenIsInvalid {

        @Test
        void returnsUnauthorizedWithExceptionMessage() throws Exception {
            willThrow(new BusinessException(AuthExceptionCase.TOKEN_INVALID))
                    .given(logoutUseCase).logout(any(LogoutCommand.class));

            mockMvc.perform(post("/api/v1/auth/logout").cookie(REFRESH_TOKEN_COOKIE))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(AuthExceptionCase.TOKEN_INVALID.getMessage()))
                    .andExpect(cookie().doesNotExist("refresh_token"));
        }
    }
}
