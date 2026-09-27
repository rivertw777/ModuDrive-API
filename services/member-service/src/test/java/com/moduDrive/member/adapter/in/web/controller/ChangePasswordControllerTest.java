package com.moduDrive.member.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.member.application.port.in.command.ChangePasswordCommand;
import com.moduDrive.member.application.port.in.usecase.ChangePasswordUseCase;
import com.moduDrive.member.domain.model.Member.MemberId;
import com.moduDrive.member.domain.model.Member.MemberPassword;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChangePasswordController.class)
@Import(GlobalExceptionHandler.class)
class ChangePasswordControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ChangePasswordUseCase changePasswordUseCase;

    private final UUID memberId = UUID.randomUUID();

    @Nested
    @DisplayName("현재 비밀번호와 8자 이상 새 비밀번호를 보낼 때")
    class WhenRequestIsValid {

        @Test
        void changesPasswordOfTheSignedInMember() throws Exception {
            mockMvc.perform(patch("/api/v1/member/password")
                            .header("X_USER_ID", memberId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\"}"))
                    .andExpect(status().isOk());

            then(changePasswordUseCase).should().changePassword(new ChangePasswordCommand(
                    new MemberId(memberId), new MemberPassword("old-password"), new MemberPassword("new-password")));
        }
    }

    @Nested
    @DisplayName("새 비밀번호가 8자 미만일 때")
    class WhenNewPasswordIsTooShort {

        @Test
        void returnsBadRequest() throws Exception {
            mockMvc.perform(patch("/api/v1/member/password")
                            .header("X_USER_ID", memberId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"old-password\",\"newPassword\":\"short\"}"))
                    .andExpect(status().isBadRequest());

            then(changePasswordUseCase).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("현재 비밀번호가 틀렸을 때")
    class WhenCurrentPasswordIsWrong {

        @Test
        void returnsBadRequestWithMessage() throws Exception {
            willThrow(new BusinessException(MemberExceptionCase.WRONG_CURRENT_PASSWORD))
                    .given(changePasswordUseCase).changePassword(any(ChangePasswordCommand.class));

            mockMvc.perform(patch("/api/v1/member/password")
                            .header("X_USER_ID", memberId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"wrong-password\",\"newPassword\":\"new-password\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(MemberExceptionCase.WRONG_CURRENT_PASSWORD.getMessage()));
        }
    }
}
