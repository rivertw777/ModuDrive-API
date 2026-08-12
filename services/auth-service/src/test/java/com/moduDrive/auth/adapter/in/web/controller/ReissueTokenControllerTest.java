package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.adapter.in.web.mapper.AuthResponseMapper;
import com.moduDrive.auth.application.port.in.command.ReissueTokenCommand;
import com.moduDrive.auth.application.port.in.usecase.ReissueTokenUseCase;
import com.moduDrive.auth.domain.model.TokenPair;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.auth.fixture.TokenPairTestFixture;
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
import org.springframework.test.context.TestPropertySource;
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

@WebMvcTest(ReissueTokenController.class)
@Import({GlobalExceptionHandler.class, AuthResponseMapper.class, RefreshTokenCookieFactory.class})
@TestPropertySource(properties = {
        "jwt.refreshToken.expiration=604800000",
        "jwt.refreshToken.cookie.secure=true"
})
class ReissueTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ReissueTokenUseCase reissueTokenUseCase;

    private static final Cookie REFRESH_TOKEN_COOKIE = new Cookie("refresh_token", "old-refresh-token");

    @Nested
    @DisplayName("유효한 리프레시 토큰 쿠키가 있을 때")
    class WhenRefreshTokenCookieIsPresent {

        @Test
        void passesCookieValueToUseCase() throws Exception {
            TokenPair tokenPair = TokenPairTestFixture.aTokenPair();
            given(reissueTokenUseCase.reissueToken(any(ReissueTokenCommand.class))).willReturn(tokenPair);

            mockMvc.perform(post("/api/v1/auth/reissue").cookie(REFRESH_TOKEN_COOKIE))
                    .andExpect(status().isOk());

            ArgumentCaptor<ReissueTokenCommand> captor = ArgumentCaptor.forClass(ReissueTokenCommand.class);
            then(reissueTokenUseCase).should().reissueToken(captor.capture());
            assertThat(captor.getValue().getRefreshToken().getTokenValue()).isEqualTo("old-refresh-token");
        }

        @Test
        void returnsAccessTokenAndRotatesRefreshTokenCookie() throws Exception {
            TokenPair tokenPair = TokenPairTestFixture.aTokenPair();
            given(reissueTokenUseCase.reissueToken(any(ReissueTokenCommand.class))).willReturn(tokenPair);

            mockMvc.perform(post("/api/v1/auth/reissue").cookie(REFRESH_TOKEN_COOKIE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.data.grantType").value("Bearer"))
                    .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                    .andExpect(cookie().value("refresh_token", "refresh-token"))
                    .andExpect(cookie().httpOnly("refresh_token", true))
                    .andExpect(cookie().secure("refresh_token", true))
                    .andExpect(cookie().maxAge("refresh_token", 604800));
        }
    }

    @Nested
    @DisplayName("리프레시 토큰 쿠키가 없을 때")
    class WhenRefreshTokenCookieIsAbsent {

        @Test
        void returnsUnauthorizedWithoutCallingUseCase() throws Exception {
            mockMvc.perform(post("/api/v1/auth/reissue"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(AuthExceptionCase.TOKEN_INVALID.getMessage()));

            then(reissueTokenUseCase).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("리프레시 토큰이 만료되었을 때")
    class WhenRefreshTokenIsExpired {

        @Test
        void returnsUnauthorizedWithExceptionMessage() throws Exception {
            willThrow(new BusinessException(AuthExceptionCase.TOKEN_EXPIRED))
                    .given(reissueTokenUseCase).reissueToken(any(ReissueTokenCommand.class));

            mockMvc.perform(post("/api/v1/auth/reissue").cookie(REFRESH_TOKEN_COOKIE))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(AuthExceptionCase.TOKEN_EXPIRED.getMessage()))
                    .andExpect(cookie().doesNotExist("refresh_token"));
        }
    }
}
