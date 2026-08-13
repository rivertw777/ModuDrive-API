package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.adapter.in.web.mapper.AuthResponseMapper;
import com.moduDrive.auth.application.port.in.command.LoginCommand;
import com.moduDrive.auth.application.port.in.usecase.LoginUseCase;
import com.moduDrive.auth.domain.model.TokenPair;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.auth.fixture.TokenPairTestFixture;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoginController.class)
@Import({GlobalExceptionHandler.class, AuthResponseMapper.class, RefreshTokenCookieFactory.class})
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private LoginUseCase loginUseCase;

    private static final String REQUEST_JSON = """
            {"email":"river@modudrive.com","password":"raw-password"}
            """;

    @Nested
    @DisplayName("유효한 로그인 요청일 때")
    class WhenRequestIsValid {

        @Test
        void returnsAccessTokenInBodyWithoutRefreshToken() throws Exception {
            TokenPair tokenPair = TokenPairTestFixture.aTokenPair();
            given(loginUseCase.login(any(LoginCommand.class))).willReturn(tokenPair);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.data.grantType").value("Bearer"))
                    .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
        }

        @Test
        void setsRefreshTokenAsHttpOnlyCookie() throws Exception {
            TokenPair tokenPair = TokenPairTestFixture.aTokenPair();
            given(loginUseCase.login(any(LoginCommand.class))).willReturn(tokenPair);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isOk())
                    .andExpect(cookie().value("refresh_token", "refresh-token"))
                    .andExpect(cookie().httpOnly("refresh_token", true))
                    .andExpect(cookie().secure("refresh_token", true))
                    .andExpect(cookie().path("refresh_token", "/api/v1/auth"))
                    .andExpect(cookie().maxAge("refresh_token", 604800))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=None")));
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
        void returnsUnauthorizedWithExceptionMessage() throws Exception {
            willThrow(new BusinessException(AuthExceptionCase.MEMBER_NOT_VALID))
                    .given(loginUseCase).login(any(LoginCommand.class));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(AuthExceptionCase.MEMBER_NOT_VALID.getMessage()))
                    .andExpect(cookie().doesNotExist("refresh_token"));
        }
    }
}
