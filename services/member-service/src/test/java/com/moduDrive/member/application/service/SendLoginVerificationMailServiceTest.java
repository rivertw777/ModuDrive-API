package com.moduDrive.member.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.member.application.port.in.command.SendLoginVerificationMailCommand;
import com.moduDrive.member.application.port.out.FindMemberPort;
import com.moduDrive.member.application.port.out.PublishMailEventPort;
import com.moduDrive.member.domain.model.Member.MemberId;
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
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SendLoginVerificationMailServiceTest {

    @Mock
    private FindMemberPort findMemberPort;
    @Mock
    private PublishMailEventPort publishMailEventPort;
    @InjectMocks
    private SendLoginVerificationMailService service;

    private final MemberId memberId = new MemberId(UUID.randomUUID());
    private final SendLoginVerificationMailCommand command = new SendLoginVerificationMailCommand(memberId, "042917");

    @Nested
    @DisplayName("회원이 있을 때")
    class WhenMemberExists {

        @Test
        @DisplayName("그 회원의 가입 이메일로 로그인 인증 메일을 보낸다")
        void mailsTheCodeToTheMembersOwnAddress() {
            given(findMemberPort.findMemberById(memberId)).willReturn(MemberTestFixture.aMemberWithId(memberId));

            service.sendLoginVerificationMail(command);

            then(publishMailEventPort).should().publishLoginVerificationRequested("river@modudrive.com", "042917");
        }
    }

    @Nested
    @DisplayName("회원이 없을 때")
    class WhenMemberIsMissing {

        @Test
        void sendsNothing() {
            given(findMemberPort.findMemberById(memberId))
                    .willThrow(new BusinessException(MemberExceptionCase.MEMBER_NOT_FOUND));

            Throwable thrown = catchThrowable(() -> service.sendLoginVerificationMail(command));

            assertThat(thrown).isInstanceOf(BusinessException.class);
            then(publishMailEventPort).shouldHaveNoInteractions();
        }
    }
}
