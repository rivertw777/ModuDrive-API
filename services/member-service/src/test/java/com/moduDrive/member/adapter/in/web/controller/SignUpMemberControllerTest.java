package com.moduDrive.member.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.member.application.port.in.command.SignUpMemberCommand;
import com.moduDrive.member.application.port.in.usecase.SignUpMemberUseCase;
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

@WebMvcTest(SignUpMemberController.class)
@Import(GlobalExceptionHandler.class)
class SignUpMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private SignUpMemberUseCase signUpMemberUseCase;

    private static final String REQUEST_JSON = """
            {"name":"river","email":"river@modudrive.com","password":"raw-password"}
            """;

    @Nested
    @DisplayName("유효한 회원가입 요청일 때")
    class WhenRequestIsValid {

        @Test
        void returnsSuccessResponse() throws Exception {
            mockMvc.perform(post("/api/v1/member/sign-up")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("success"));

            then(signUpMemberUseCase).should().signUpMember(any(SignUpMemberCommand.class));
        }
    }

    @Nested
    @DisplayName("요청 값 검증에 실패했을 때")
    class WhenRequestIsInvalid {

        @Test
        void returnsBadRequest() throws Exception {
            String invalidJson = """
                    {"name":"","email":"not-an-email","password":"short"}
                    """;

            mockMvc.perform(post("/api/v1/member/sign-up")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());

            then(signUpMemberUseCase).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("이미 존재하는 이메일일 때")
    class WhenEmailIsDuplicate {

        @Test
        void returnsBadRequestWithExceptionMessage() throws Exception {
            willThrow(new BusinessException(MemberExceptionCase.DUPLICATE_EMAIL))
                    .given(signUpMemberUseCase).signUpMember(any(SignUpMemberCommand.class));

            mockMvc.perform(post("/api/v1/member/sign-up")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(MemberExceptionCase.DUPLICATE_EMAIL.getMessage()));
        }
    }
}
