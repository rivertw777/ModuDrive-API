package com.moduDrive.member.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.member.application.event.MemberSignedUpEvent;
import com.moduDrive.member.application.port.in.command.SignUpMemberCommand;
import com.moduDrive.member.application.port.out.CheckEmailExistsPort;
import com.moduDrive.member.application.port.out.CreateNamespacePort;
import com.moduDrive.member.application.port.out.EmailVerificationTokenPort;
import com.moduDrive.member.application.port.out.EncodePasswordPort;
import com.moduDrive.member.application.port.out.SignUpMemberPort;
import com.moduDrive.member.domain.model.Member;
import com.moduDrive.member.domain.model.Member.MemberEmail;
import com.moduDrive.member.domain.model.Member.MemberId;
import com.moduDrive.member.domain.model.Member.MemberIsValid;
import com.moduDrive.member.domain.model.Member.MemberName;
import com.moduDrive.member.domain.model.Member.MemberPassword;
import com.moduDrive.member.domain.model.Member.MemberRoles;
import com.moduDrive.member.domain.model.Role;
import com.moduDrive.member.exception.MemberExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SignUpMemberServiceTest {

    @Mock
    private SignUpMemberPort signUpMemberPort;
    @Mock
    private EncodePasswordPort encodePasswordPort;
    @Mock
    private CheckEmailExistsPort checkEmailExistsPort;
    @Mock
    private CreateNamespacePort createNamespacePort;
    @Mock
    private EmailVerificationTokenPort emailVerificationTokenPort;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private SignUpMemberService signUpMemberService;

    private final SignUpMemberCommand command = new SignUpMemberCommand(
            new MemberName("river"),
            new MemberEmail("river@modudrive.com"),
            new MemberPassword("raw-password"));

    @Nested
    @DisplayName("이메일이 중복되지 않았을 때")
    class WhenEmailIsUnique {

        private final UUID memberId = UUID.randomUUID();
        private final Member savedMember = Member.withId(
                new MemberId(memberId), command.getMemberName(), command.getMemberEmail(),
                new MemberPassword("encoded-password"), new MemberRoles(List.of(Role.MEMBER)),
                new MemberIsValid(false));

        @Test
        void encodesPasswordAndCreatesUnverifiedMember() {
            given(checkEmailExistsPort.existsByEmail(command.getMemberEmail())).willReturn(false);
            given(encodePasswordPort.encodePassword(command.getMemberPassword()))
                    .willReturn(new MemberPassword("encoded-password"));
            given(signUpMemberPort.createMember(any(Member.class))).willReturn(savedMember);

            signUpMemberService.signUpMember(command);

            then(signUpMemberPort).should().createMember(
                    argThat((Member m) -> !m.isValid() && m.getEmail().equals(command.getMemberEmail().emailValue())));
            then(createNamespacePort).should().createNamespace(memberId);
        }

        @Test
        void issuesVerificationTokenAndPublishesSignedUpEvent() {
            given(checkEmailExistsPort.existsByEmail(command.getMemberEmail())).willReturn(false);
            given(encodePasswordPort.encodePassword(command.getMemberPassword()))
                    .willReturn(new MemberPassword("encoded-password"));
            given(signUpMemberPort.createMember(any(Member.class))).willReturn(savedMember);

            signUpMemberService.signUpMember(command);

            then(emailVerificationTokenPort).should().saveToken(anyString(), eq(memberId));
            then(eventPublisher).should().publishEvent(argThat((MemberSignedUpEvent event) ->
                    event.memberId().equals(memberId)
                            && event.email().equals(command.getMemberEmail().emailValue())
                            && event.name().equals(command.getMemberName().nameValue())
                            && event.verificationToken() != null));
        }
    }

    @Nested
    @DisplayName("이메일이 이미 존재할 때")
    class WhenEmailIsDuplicate {

        @Test
        void throwsBusinessExceptionAndSkipsCreation() {
            given(checkEmailExistsPort.existsByEmail(command.getMemberEmail())).willReturn(true);

            Throwable thrown = catchThrowable(() -> signUpMemberService.signUpMember(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(MemberExceptionCase.DUPLICATE_EMAIL);
            then(encodePasswordPort).shouldHaveNoInteractions();
            then(signUpMemberPort).shouldHaveNoInteractions();
            then(emailVerificationTokenPort).shouldHaveNoInteractions();
            then(eventPublisher).shouldHaveNoInteractions();
        }
    }
}
