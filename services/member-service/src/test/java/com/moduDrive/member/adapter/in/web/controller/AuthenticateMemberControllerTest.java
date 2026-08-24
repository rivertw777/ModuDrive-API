package com.moduDrive.member.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.member.adapter.in.web.mapper.MemberResponseMapper;
import com.moduDrive.member.application.port.in.command.AuthenticateMemberCommand;
import com.moduDrive.member.application.port.in.usecase.AuthenticateMemberUseCase;
import com.moduDrive.member.domain.model.Member;
import com.moduDrive.member.domain.model.Member.MemberId;
import com.moduDrive.member.domain.model.Role;
import com.moduDrive.member.exception.MemberExceptionCase;
import com.moduDrive.member.fixture.MemberTestFixture;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthenticateMemberController.class)
@Import({GlobalExceptionHandler.class, MemberResponseMapper.class})
class AuthenticateMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AuthenticateMemberUseCase authenticateMemberUseCase;

    private static final String REQUEST_JSON = """
            {"email":"river@modudrive.com","password":"raw-password"}
            """;

    @Nested
    @DisplayName("인증 정보가 일치할 때")
    class WhenCredentialsMatch {

        @Test
        void returnsAuthenticatedMemberResponse() throws Exception {
            Member member = MemberTestFixture.aMemberWithId(new MemberId(UUID.randomUUID()));
            given(authenticateMemberUseCase.authenticateMember(any(AuthenticateMemberCommand.class)))
                    .willReturn(member);

            mockMvc.perform(post("/internal/v1/member/authenticate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.email").value("river@modudrive.com"))
                    .andExpect(jsonPath("$.data.roles[0]").value(Role.MEMBER.name()));
        }
    }

    @Nested
    @DisplayName("비밀번호가 일치하지 않을 때")
    class WhenPasswordDoesNotMatch {

        @Test
        void returnsBadRequestWithExceptionMessage() throws Exception {
            willThrow(new BusinessException(MemberExceptionCase.PASSWORD_NOT_MATCHED))
                    .given(authenticateMemberUseCase).authenticateMember(any(AuthenticateMemberCommand.class));

            mockMvc.perform(post("/internal/v1/member/authenticate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(MemberExceptionCase.PASSWORD_NOT_MATCHED.getMessage()));
        }
    }
}
