package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.adapter.in.web.mapper.AuthResponseMapper;
import com.moduDrive.auth.application.port.in.command.ValidateSessionCommand;
import com.moduDrive.auth.application.port.in.usecase.ValidateSessionUseCase;
import com.moduDrive.auth.domain.vo.SessionId;
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

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ValidateSessionController.class)
@Import({GlobalExceptionHandler.class, AuthResponseMapper.class})
class ValidateSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ValidateSessionUseCase validateSessionUseCase;

    @Nested
    @DisplayName("살아 있는 세션일 때")
    class WhenSessionIsLive {

        @Test
        void returnsMemberIdAndRoles() throws Exception {
            given(validateSessionUseCase.validateSession(new ValidateSessionCommand(new SessionId("session-id"), false)))
                    .willReturn(MemberAuthDataTestFixture.aMemberAuthData());

            mockMvc.perform(post("/internal/v1/auth/sessions/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"sessionId":"session-id","touch":false}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.memberId").value("member-id"))
                    .andExpect(jsonPath("$.data.memberRoles[0]").value("MEMBER"));
        }
    }

    @Nested
    @DisplayName("세션이 없을 때")
    class WhenSessionIsMissing {

        @Test
        void returnsUnauthorized() throws Exception {
            given(validateSessionUseCase.validateSession(new ValidateSessionCommand(new SessionId("session-id"), true)))
                    .willThrow(new BusinessException(AuthExceptionCase.SESSION_NOT_FOUND));

            mockMvc.perform(post("/internal/v1/auth/sessions/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"sessionId":"session-id","touch":true}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(AuthExceptionCase.SESSION_NOT_FOUND.getMessage()));
        }
    }

    @Nested
    @DisplayName("세션 ID가 비어 있을 때")
    class WhenSessionIdIsBlank {

        @Test
        void returnsBadRequest() throws Exception {
            mockMvc.perform(post("/internal/v1/auth/sessions/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"sessionId":"","touch":true}
                                    """))
                    .andExpect(status().isBadRequest());
        }
    }
}
