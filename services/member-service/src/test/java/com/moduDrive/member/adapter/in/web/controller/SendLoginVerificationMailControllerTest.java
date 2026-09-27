package com.moduDrive.member.adapter.in.web.controller;

import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.member.application.port.in.command.SendLoginVerificationMailCommand;
import com.moduDrive.member.application.port.in.usecase.SendLoginVerificationMailUseCase;
import com.moduDrive.member.domain.model.Member.MemberId;
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
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SendLoginVerificationMailController.class)
@Import(GlobalExceptionHandler.class)
class SendLoginVerificationMailControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private SendLoginVerificationMailUseCase sendLoginVerificationMailUseCase;

    private final UUID memberId = UUID.randomUUID();

    @Nested
    @DisplayName("회원 ID와 코드가 오면")
    class WhenRequestIsValid {

        @Test
        void asksTheUseCaseToMailTheCode() throws Exception {
            mockMvc.perform(post("/internal/v1/member/login-verification-mail")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"memberId\":\"" + memberId + "\",\"code\":\"042917\"}"))
                    .andExpect(status().isOk());

            then(sendLoginVerificationMailUseCase).should().sendLoginVerificationMail(
                    new SendLoginVerificationMailCommand(new MemberId(memberId), "042917"));
        }
    }

    @Nested
    @DisplayName("코드가 비어 있으면")
    class WhenCodeIsBlank {

        @Test
        void returnsBadRequest() throws Exception {
            mockMvc.perform(post("/internal/v1/member/login-verification-mail")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"memberId\":\"" + memberId + "\",\"code\":\"\"}"))
                    .andExpect(status().isBadRequest());

            then(sendLoginVerificationMailUseCase).should(never()).sendLoginVerificationMail(any());
        }
    }
}
