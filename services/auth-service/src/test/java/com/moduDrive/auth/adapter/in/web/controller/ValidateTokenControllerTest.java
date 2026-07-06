package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.adapter.in.web.mapper.AuthResponseMapper;
import com.moduDrive.auth.application.port.in.command.ValidateTokenCommand;
import com.moduDrive.auth.application.port.in.usecase.ValidateTokenUseCase;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.auth.fixture.MemberAuthDataTestFixture;
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

@WebMvcTest(ValidateTokenController.class)
@Import({GlobalExceptionHandler.class, AuthResponseMapper.class})
class ValidateTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ValidateTokenUseCase validateTokenUseCase;

    private static final String REQUEST_JSON = """
            {"token":"access-token"}
            """;

    @Nested
    @DisplayName("유효한 토큰일 때")
    class WhenTokenIsValid {

        @Test
        void returnsMemberAuthDataResponse() throws Exception {
            MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthData();
            given(validateTokenUseCase.validateToken(any(ValidateTokenCommand.class))).willReturn(memberAuthData);

            mockMvc.perform(post("/api/v1/auth/validate-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.memberId").value("member-id"))
                    .andExpect(jsonPath("$.data.memberRoles[0]").value("MEMBER"));
        }
    }

    @Nested
    @DisplayName("토큰이 만료되었을 때")
    class WhenTokenIsExpired {

        @Test
        void returnsUnauthorizedWithExceptionMessage() throws Exception {
            willThrow(new BusinessException(AuthExceptionCase.TOKEN_EXPIRED))
                    .given(validateTokenUseCase).validateToken(any(ValidateTokenCommand.class));

            mockMvc.perform(post("/api/v1/auth/validate-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(AuthExceptionCase.TOKEN_EXPIRED.getMessage()));
        }
    }

    @Nested
    @DisplayName("요청 값 검증에 실패했을 때")
    class WhenRequestIsInvalid {

        @Test
        void returnsBadRequest() throws Exception {
            String invalidJson = """
                    {"token":""}
                    """;

            mockMvc.perform(post("/api/v1/auth/validate-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());
        }
    }
}
