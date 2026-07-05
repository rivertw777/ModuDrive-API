package com.moduDrive.member.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.member.application.port.in.command.AuthenticateMemberCommand;
import com.moduDrive.member.application.port.out.FindMemberPort;
import com.moduDrive.member.application.port.out.MatchesPasswordPort;
import com.moduDrive.member.domain.model.Member;
import com.moduDrive.member.domain.model.Member.MemberEmail;
import com.moduDrive.member.domain.model.Member.MemberId;
import com.moduDrive.member.domain.model.Member.MemberPassword;
import com.moduDrive.member.exception.MemberExceptionCase;
import com.moduDrive.member.fixture.MemberTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthenticateMemberServiceTest {

    @Mock
    private FindMemberPort findMemberPort;
    @Mock
    private MatchesPasswordPort matchesPasswordPort;
    @InjectMocks
    private AuthenticateMemberService authenticateMemberService;

    private final MemberEmail memberEmail = MemberTestFixture.DEFAULT_EMAIL;
    private final MemberPassword rawPassword = new MemberPassword("raw-password");
    private final AuthenticateMemberCommand command = new AuthenticateMemberCommand(memberEmail, rawPassword);
    private final Member member = MemberTestFixture.aMemberWithId(new MemberId(UUID.randomUUID()));

    @Nested
    @DisplayName("비밀번호가 일치할 때")
    class WhenPasswordMatches {

        @Test
        void returnsAuthenticatedMember() {
            given(findMemberPort.findMemberByEmail(memberEmail)).willReturn(member);
            given(matchesPasswordPort.matchesPassword(rawPassword, new MemberPassword(member.getPassword())))
                    .willReturn(true);

            Member result = authenticateMemberService.authenticateMember(command);

            assertThat(result).isEqualTo(member);
        }
    }

    @Nested
    @DisplayName("비밀번호가 일치하지 않을 때")
    class WhenPasswordDoesNotMatch {

        @Test
        void throwsBusinessException() {
            given(findMemberPort.findMemberByEmail(memberEmail)).willReturn(member);
            given(matchesPasswordPort.matchesPassword(rawPassword, new MemberPassword(member.getPassword())))
                    .willReturn(false);

            Throwable thrown = catchThrowable(() -> authenticateMemberService.authenticateMember(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(MemberExceptionCase.PASSWORD_NOT_MATCHED);
        }
    }
}
