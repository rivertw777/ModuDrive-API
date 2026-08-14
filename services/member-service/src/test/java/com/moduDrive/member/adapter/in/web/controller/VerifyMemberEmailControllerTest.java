package com.moduDrive.member.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.member.application.port.in.command.VerifyMemberEmailCommand;
import com.moduDrive.member.application.port.in.usecase.VerifyMemberEmailUseCase;
import com.moduDrive.member.exception.MemberExceptionCase;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VerifyMemberEmailController.class)
@Import(GlobalExceptionHandler.class)
class VerifyMemberEmailControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private VerifyMemberEmailUseCase verifyMemberEmailUseCase;

    private static final String TOKEN = "some-token";

    @Nested
    @DisplayName("유효한 토큰으로 요청했을 때")
    class WhenTokenIsValid {

        @Test
        void returnsSuccessResponse() throws Exception {
            mockMvc.perform(get("/api/v1/member/verify-email").param("token", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("success"));

            then(verifyMemberEmailUseCase).should().verifyMemberEmail(new VerifyMemberEmailCommand(TOKEN));
        }
    }

    @Nested
    @DisplayName("유효하지 않은 토큰으로 요청했을 때")
    class WhenTokenIsInvalid {

        @Test
        void returnsBadRequestWithExceptionMessage() throws Exception {
            willThrow(new BusinessException(MemberExceptionCase.INVALID_VERIFICATION_TOKEN))
                    .given(verifyMemberEmailUseCase).verifyMemberEmail(new VerifyMemberEmailCommand(TOKEN));

            mockMvc.perform(get("/api/v1/member/verify-email").param("token", TOKEN))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(MemberExceptionCase.INVALID_VERIFICATION_TOKEN.getMessage()));
        }
    }
}
