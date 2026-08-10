package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.adapter.in.web.mapper.AuthResponseMapper;
import com.moduDrive.auth.application.port.in.command.ReissueTokenCommand;
import com.moduDrive.auth.application.port.in.usecase.ReissueTokenUseCase;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReissueTokenController.class)
@Import({GlobalExceptionHandler.class, AuthResponseMapper.class})
class ReissueTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ReissueTokenUseCase reissueTokenUseCase;

    private static final String REQUEST_JSON = """
            {"refreshToken":"refresh-token"}
            """;

    @Nested
    @DisplayName("유효한 리프레시 토큰 요청일 때")
    class WhenRequestIsValid {

        @Test
        void returnsNewTokenPairResponse() throws Exception {
            TokenPair tokenPair = TokenPairTestFixture.aTokenPair();
            given(reissueTokenUseCase.reissueToken(any(ReissueTokenCommand.class))).willReturn(tokenPair);

            mockMvc.perform(post("/api/v1/auth/reissue")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                    .andExpect(jsonPath("$.data.grantType").value("Bearer"));
        }
    }

    @Nested
    @DisplayName("요청 값 검증에 실패했을 때")
    class WhenRequestIsInvalid {

        @Test
        void returnsBadRequest() throws Exception {
            String invalidJson = """
                    {"refreshToken":""}
                    """;

            mockMvc.perform(post("/api/v1/auth/reissue")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("리프레시 토큰이 만료되었을 때")
    class WhenRefreshTokenIsExpired {

        @Test
        void returnsUnauthorizedWithExceptionMessage() throws Exception {
            willThrow(new BusinessException(AuthExceptionCase.TOKEN_EXPIRED))
                    .given(reissueTokenUseCase).reissueToken(any(ReissueTokenCommand.class));

            mockMvc.perform(post("/api/v1/auth/reissue")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(AuthExceptionCase.TOKEN_EXPIRED.getMessage()));
        }
    }
}
