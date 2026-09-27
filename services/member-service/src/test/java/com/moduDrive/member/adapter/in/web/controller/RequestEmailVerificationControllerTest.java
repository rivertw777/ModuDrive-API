package com.moduDrive.member.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.member.application.port.in.command.RequestEmailVerificationCommand;
import com.moduDrive.member.application.port.in.usecase.RequestEmailVerificationUseCase;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RequestEmailVerificationController.class)
@Import(GlobalExceptionHandler.class)
class RequestEmailVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private RequestEmailVerificationUseCase requestEmailVerificationUseCase;

    private static final String REQUEST_JSON = """
            {"email":"river@modudrive.com"}
            """;

    @Nested
    @DisplayName("유효한 이메일로 요청했을 때")
    class WhenRequestIsValid {

        @Test
        void returnsSuccessResponse() throws Exception {
            mockMvc.perform(post("/api/v1/member/verify-email/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("success"));

            then(requestEmailVerificationUseCase).should()
                    .requestEmailVerification(any(RequestEmailVerificationCommand.class));
        }
    }

    @Nested
    @DisplayName("요청 값 검증에 실패했을 때")
    class WhenRequestIsInvalid {

        @Test
        void returnsBadRequest() throws Exception {
            String invalidJson = """
                    {"email":"not-an-email"}
                    """;

            mockMvc.perform(post("/api/v1/member/verify-email/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());

            then(requestEmailVerificationUseCase).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("한 주소로 너무 자주 요청했을 때")
    class WhenRequestedTooOften {

        @Test
        void returnsTooManyRequestsWithExceptionMessage() throws Exception {
            willThrow(new BusinessException(MemberExceptionCase.TOO_MANY_VERIFICATION_REQUESTS))
                    .given(requestEmailVerificationUseCase)
                    .requestEmailVerification(any(RequestEmailVerificationCommand.class));

            mockMvc.perform(post("/api/v1/member/verify-email/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.message").value(MemberExceptionCase.TOO_MANY_VERIFICATION_REQUESTS.getMessage()));
        }
    }
}
