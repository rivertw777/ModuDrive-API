package com.moduDrive.member.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.member.application.port.in.command.ChangePasswordCommand;
import com.moduDrive.member.application.port.out.ChangePasswordPort;
import com.moduDrive.member.application.port.out.EncodePasswordPort;
import com.moduDrive.member.application.port.out.FindMemberPort;
import com.moduDrive.member.application.port.out.MatchesPasswordPort;
import com.moduDrive.member.application.port.out.PublishMemberEventPort;
import com.moduDrive.member.domain.model.Member.MemberId;
import com.moduDrive.member.domain.model.Member.MemberPassword;
import com.moduDrive.member.exception.MemberExceptionCase;
import com.moduDrive.member.fixture.MemberTestFixture;
import org.junit.jupiter.api.BeforeEach;
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
class ChangePasswordServiceTest {

    @Mock
    private FindMemberPort findMemberPort;
    @Mock
    private MatchesPasswordPort matchesPasswordPort;
    @Mock
    private EncodePasswordPort encodePasswordPort;
    @Mock
    private ChangePasswordPort changePasswordPort;
    @Mock
    private PublishMemberEventPort publishMemberEventPort;
    @InjectMocks
    private ChangePasswordService changePasswordService;

    private final MemberId memberId = new MemberId(UUID.randomUUID());
    private final MemberPassword current = new MemberPassword("current-password");
    private final MemberPassword next = new MemberPassword("new-password");
    private final ChangePasswordCommand command = new ChangePasswordCommand(memberId, current, next);

    @BeforeEach
    void memberExists() {
        given(findMemberPort.findMemberById(memberId)).willReturn(MemberTestFixture.aMemberWithId(memberId));
    }

    @Nested
    @DisplayName("현재 비밀번호가 맞을 때")
    class WhenCurrentPasswordMatches {

        @Test
        void savesEncodedPasswordAndPublishesPasswordChanged() {
            given(matchesPasswordPort.matchesPassword(current, MemberTestFixture.DEFAULT_PASSWORD)).willReturn(true);
            given(encodePasswordPort.encodePassword(next)).willReturn(new MemberPassword("encoded-new"));

            changePasswordService.changePassword(command);

            then(changePasswordPort).should().changePassword(memberId, new MemberPassword("encoded-new"));
            then(publishMemberEventPort).should().publishPasswordChanged(memberId.idValue());
        }
    }

    @Nested
    @DisplayName("현재 비밀번호가 틀렸을 때")
    class WhenCurrentPasswordIsWrong {

        @Test
        void throwsWithoutChangingAnything() {
            given(matchesPasswordPort.matchesPassword(current, MemberTestFixture.DEFAULT_PASSWORD)).willReturn(false);

            Throwable thrown = catchThrowable(() -> changePasswordService.changePassword(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(MemberExceptionCase.WRONG_CURRENT_PASSWORD);
            then(changePasswordPort).shouldHaveNoInteractions();
            then(publishMemberEventPort).shouldHaveNoInteractions();
        }
    }
}
