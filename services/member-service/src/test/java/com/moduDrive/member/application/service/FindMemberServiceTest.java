package com.moduDrive.member.application.service;

import com.moduDrive.member.application.port.in.command.FindMemberCommand;
import com.moduDrive.member.application.port.out.FindMemberPort;
import com.moduDrive.member.domain.model.Member;
import com.moduDrive.member.domain.model.Member.MemberId;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class FindMemberServiceTest {

    @Mock
    private FindMemberPort findMemberPort;
    @InjectMocks
    private FindMemberService findMemberService;

    private final MemberId memberId = new MemberId(UUID.randomUUID());
    private final FindMemberCommand command = new FindMemberCommand(memberId);

    @Nested
    @DisplayName("존재하는 회원을 조회할 때")
    class WhenMemberExists {

        @Test
        void returnsMemberFromPort() {
            Member member = MemberTestFixture.aMemberWithId(memberId);
            given(findMemberPort.findMemberById(memberId)).willReturn(member);

            Member result = findMemberService.findMember(command);

            assertThat(result).isEqualTo(member);
            then(findMemberPort).should().findMemberById(memberId);
        }
    }
}
