package com.moduDrive.member.application.service;

import com.moduDrive.common.core.exception.BusinessException;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
    @InjectMocks
    private SignUpMemberService signUpMemberService;

    private final SignUpMemberCommand command = new SignUpMemberCommand(
            new MemberName("river"),
            new MemberEmail("river@modudrive.com"),
            new MemberPassword("raw-password"));

    @Nested
    @DisplayName("이메일이 중복되지 않고 사전 인증이 완료됐을 때")
    class WhenEmailIsUniqueAndVerified {

        private final UUID memberId = UUID.randomUUID();
        private final Member savedMember = Member.withId(
                new MemberId(memberId), command.getMemberName(), command.getMemberEmail(),
                new MemberPassword("encoded-password"), new MemberRoles(List.of(Role.MEMBER)),
                new MemberIsValid(true));

        @Test
        void encodesPasswordAndCreatesValidMember() {
            given(checkEmailExistsPort.existsByEmail(command.getMemberEmail())).willReturn(false);
            given(emailVerificationTokenPort.consumeVerified(command.getMemberEmail().emailValue())).willReturn(true);
            given(encodePasswordPort.encodePassword(command.getMemberPassword()))
                    .willReturn(new MemberPassword("encoded-password"));
            given(signUpMemberPort.createMember(any(Member.class))).willReturn(savedMember);

            signUpMemberService.signUpMember(command);

            then(signUpMemberPort).should().createMember(
                    argThat((Member m) -> m.isValid() && m.getEmail().equals(command.getMemberEmail().emailValue())));
            then(createNamespacePort).should().createNamespace(memberId);
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
        }
    }

    @Nested
    @DisplayName("이메일 사전 인증이 완료되지 않았을 때")
    class WhenEmailIsNotVerified {

        @Test
        void throwsBusinessExceptionAndSkipsCreation() {
            given(checkEmailExistsPort.existsByEmail(command.getMemberEmail())).willReturn(false);
            given(emailVerificationTokenPort.consumeVerified(command.getMemberEmail().emailValue())).willReturn(false);

            Throwable thrown = catchThrowable(() -> signUpMemberService.signUpMember(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(MemberExceptionCase.EMAIL_NOT_VERIFIED);
            then(encodePasswordPort).shouldHaveNoInteractions();
            then(signUpMemberPort).shouldHaveNoInteractions();
        }
    }
}
