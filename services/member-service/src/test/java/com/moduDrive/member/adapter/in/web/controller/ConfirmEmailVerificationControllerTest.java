package com.moduDrive.member.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.member.application.port.in.command.ConfirmEmailVerificationCommand;
import com.moduDrive.member.application.port.in.usecase.ConfirmEmailVerificationUseCase;
import com.moduDrive.member.domain.model.Member.MemberEmail;
import com.moduDrive.member.exception.MemberExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConfirmEmailVerificationController.class)
@Import(GlobalExceptionHandler.class)
class ConfirmEmailVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ConfirmEmailVerificationUseCase confirmEmailVerificationUseCase;

    private static final String REQUEST_JSON = """
            {"email":"river@modudrive.com","code":"042917"}
            """;

    private static final ConfirmEmailVerificationCommand COMMAND =
            new ConfirmEmailVerificationCommand(new MemberEmail("river@modudrive.com"), "042917");

    @Nested
    @DisplayName("유효한 인증 코드로 요청했을 때")
    class WhenCodeIsValid {

        @Test
        void returnsSuccessResponse() throws Exception {
            mockMvc.perform(post("/api/v1/member/verify-email/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("success"));

            then(confirmEmailVerificationUseCase).should().confirmEmailVerification(COMMAND);
        }
    }

    @Nested
    @DisplayName("요청 값 검증에 실패했을 때")
    class WhenRequestIsInvalid {

        @Test
        void returnsBadRequest() throws Exception {
            String invalidJson = """
                    {"email":"river@modudrive.com","code":""}
                    """;

            mockMvc.perform(post("/api/v1/member/verify-email/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());

            then(confirmEmailVerificationUseCase).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("유효하지 않은 인증 코드로 요청했을 때")
    class WhenCodeIsInvalid {

        @Test
        void returnsBadRequestWithExceptionMessage() throws Exception {
            willThrow(new BusinessException(MemberExceptionCase.INVALID_VERIFICATION_CODE))
                    .given(confirmEmailVerificationUseCase)
                    .confirmEmailVerification(COMMAND);

            mockMvc.perform(post("/api/v1/member/verify-email/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(MemberExceptionCase.INVALID_VERIFICATION_CODE.getMessage()));
        }
    }
}
