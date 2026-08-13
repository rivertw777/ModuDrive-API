package com.moduDrive.member.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.member.adapter.in.web.mapper.MemberResponseMapper;
import com.moduDrive.member.application.port.in.command.FindMemberByEmailCommand;
import com.moduDrive.member.application.port.in.usecase.FindMemberByEmailUseCase;
import com.moduDrive.member.domain.model.Member;
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

@WebMvcTest(FindMemberByEmailController.class)
@Import({GlobalExceptionHandler.class, MemberResponseMapper.class})
class FindMemberByEmailControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private FindMemberByEmailUseCase findMemberByEmailUseCase;

    private final UUID memberId = UUID.randomUUID();

    @Nested
    @DisplayName("존재하는 회원의 이메일로 조회할 때")
    class WhenMemberExists {

        @Test
        void returnsIdNameEmailOnly() throws Exception {
            Member member = MemberTestFixture.aMemberWithId(new MemberId(memberId));
            given(findMemberByEmailUseCase.findMemberByEmail(any(FindMemberByEmailCommand.class)))
                    .willReturn(member);

            mockMvc.perform(get("/api/v1/member/find-by-email").param("email", "river@modudrive.com"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(memberId.toString()))
                    .andExpect(jsonPath("$.data.name").value("river"))
                    .andExpect(jsonPath("$.data.email").value("river@modudrive.com"))
                    .andExpect(jsonPath("$.data.password").doesNotExist())
                    .andExpect(jsonPath("$.data.isValid").doesNotExist());
        }
    }

    @Nested
    @DisplayName("email 파라미터가 없을 때")
    class WhenEmailParamIsMissing {

        @Test
        void returnsBadRequest() throws Exception {
            mockMvc.perform(get("/api/v1/member/find-by-email"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("해당 이메일의 회원이 없을 때")
    class WhenMemberNotFound {

        @Test
        void returnsBadRequestWithExceptionMessage() throws Exception {
            willThrow(new BusinessException(MemberExceptionCase.MEMBER_NOT_FOUND))
                    .given(findMemberByEmailUseCase).findMemberByEmail(any(FindMemberByEmailCommand.class));

            mockMvc.perform(get("/api/v1/member/find-by-email").param("email", "nobody@modudrive.com"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(MemberExceptionCase.MEMBER_NOT_FOUND.getMessage()));
        }
    }
}
