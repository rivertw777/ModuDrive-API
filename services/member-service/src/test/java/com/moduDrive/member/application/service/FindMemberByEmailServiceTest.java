package com.moduDrive.member.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.member.application.port.in.command.FindMemberByEmailCommand;
import com.moduDrive.member.application.port.out.FindMemberPort;
import com.moduDrive.member.domain.model.Member;
import com.moduDrive.member.domain.model.Member.MemberEmail;
import com.moduDrive.member.exception.MemberExceptionCase;
import com.moduDrive.member.fixture.MemberTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class FindMemberByEmailServiceTest {

    @Mock private FindMemberPort findMemberPort;
    @InjectMocks private FindMemberByEmailService findMemberByEmailService;

    private final MemberEmail memberEmail = new MemberEmail("river@modudrive.com");
    private final FindMemberByEmailCommand command = new FindMemberByEmailCommand(memberEmail);

    @Nested
    @DisplayName("해당 이메일의 회원이 있을 때")
    class WhenMemberExists {

        @Test
        void returnsMember() {
            Member member = MemberTestFixture.aMember();
            given(findMemberPort.findMemberByEmail(memberEmail)).willReturn(member);

            Member result = findMemberByEmailService.findMemberByEmail(command);

            assertThat(result).isSameAs(member);
        }
    }

    @Nested
    @DisplayName("해당 이메일의 회원이 없을 때")
    class WhenMemberDoesNotExist {

        @Test
        void propagatesMemberNotFound() {
            willThrow(new BusinessException(MemberExceptionCase.MEMBER_NOT_FOUND))
                    .given(findMemberPort).findMemberByEmail(memberEmail);

            Throwable thrown = catchThrowable(() -> findMemberByEmailService.findMemberByEmail(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(MemberExceptionCase.MEMBER_NOT_FOUND);
        }
    }
}
