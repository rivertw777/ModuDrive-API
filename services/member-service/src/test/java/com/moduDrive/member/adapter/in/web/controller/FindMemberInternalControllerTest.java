package com.moduDrive.member.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.member.adapter.in.web.mapper.MemberResponseMapper;
import com.moduDrive.member.application.port.in.command.FindMemberByEmailCommand;
import com.moduDrive.member.application.port.in.command.FindMemberCommand;
import com.moduDrive.member.application.port.in.usecase.FindMemberByEmailUseCase;
import com.moduDrive.member.application.port.in.usecase.FindMemberUseCase;
import com.moduDrive.member.domain.model.Member.MemberId;
import com.moduDrive.member.exception.MemberExceptionCase;
import com.moduDrive.member.fixture.MemberTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FindMemberInternalController.class)
@Import({GlobalExceptionHandler.class, MemberResponseMapper.class})
class FindMemberInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private FindMemberUseCase findMemberUseCase;
    @MockitoBean
    private FindMemberByEmailUseCase findMemberByEmailUseCase;

    private final UUID memberId = UUID.randomUUID();

    @Nested
    @DisplayName("ID로 조회할 때")
    class WhenFindingById {

        @Test
        void returnsIdNameEmailOnly() throws Exception {
            given(findMemberUseCase.findMember(any(FindMemberCommand.class)))
                    .willReturn(MemberTestFixture.aMemberWithId(new MemberId(memberId)));

            mockMvc.perform(get("/internal/v1/member/{memberId}", memberId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(memberId.toString()))
                    .andExpect(jsonPath("$.data.name").value("river"))
                    .andExpect(jsonPath("$.data.email").value("river@modudrive.com"))
                    .andExpect(jsonPath("$.data.password").doesNotExist())
                    .andExpect(jsonPath("$.data.isValid").doesNotExist());
        }

        @Test
        void returnsBadRequestWhenMemberNotFound() throws Exception {
            willThrow(new BusinessException(MemberExceptionCase.MEMBER_NOT_FOUND))
                    .given(findMemberUseCase).findMember(any(FindMemberCommand.class));

            mockMvc.perform(get("/internal/v1/member/{memberId}", memberId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(MemberExceptionCase.MEMBER_NOT_FOUND.getMessage()));
        }
    }

    @Nested
    @DisplayName("이메일로 조회할 때")
    class WhenFindingByEmail {

        @Test
        @DisplayName("ID 경로가 아니라 이메일 조회로 연결된다")
        void returnsMember() throws Exception {
            given(findMemberByEmailUseCase.findMemberByEmail(any(FindMemberByEmailCommand.class)))
                    .willReturn(MemberTestFixture.aMemberWithId(new MemberId(memberId)));

            mockMvc.perform(get("/internal/v1/member/by-email").param("email", "river@modudrive.com"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(memberId.toString()));
        }

        @Test
        void returnsBadRequestWhenMemberNotFound() throws Exception {
            willThrow(new BusinessException(MemberExceptionCase.MEMBER_NOT_FOUND))
                    .given(findMemberByEmailUseCase).findMemberByEmail(any(FindMemberByEmailCommand.class));

            mockMvc.perform(get("/internal/v1/member/by-email").param("email", "nobody@modudrive.com"))
                    .andExpect(status().isBadRequest());
        }
    }
}
