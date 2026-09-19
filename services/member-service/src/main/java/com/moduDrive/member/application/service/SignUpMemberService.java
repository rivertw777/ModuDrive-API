package com.moduDrive.member.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.transaction.AfterCommit;
import com.moduDrive.member.application.port.in.command.SignUpMemberCommand;
import com.moduDrive.member.application.port.in.usecase.SignUpMemberUseCase;
import com.moduDrive.member.application.port.out.CheckEmailExistsPort;
import com.moduDrive.member.application.port.out.CreateNamespacePort;
import com.moduDrive.member.application.port.out.EmailVerificationTokenPort;
import com.moduDrive.member.application.port.out.EncodePasswordPort;
import com.moduDrive.member.application.port.out.PublishMemberEventPort;
import com.moduDrive.member.application.port.out.SignUpMemberPort;
import com.moduDrive.member.exception.MemberExceptionCase;
import com.moduDrive.member.domain.model.Member;
import com.moduDrive.member.domain.model.Member.MemberEmail;
import com.moduDrive.member.domain.model.Member.MemberIsValid;
import com.moduDrive.member.domain.model.Member.MemberPassword;
import com.moduDrive.member.domain.model.Member.MemberRoles;
import com.moduDrive.member.domain.model.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
@RequiredArgsConstructor
class SignUpMemberService implements SignUpMemberUseCase {

    private final SignUpMemberPort signUpMemberPort;
    private final EncodePasswordPort encodePasswordPort;
    private final CheckEmailExistsPort checkEmailExistsPort;
    private final EmailVerificationTokenPort emailVerificationTokenPort;
    private final PublishMemberEventPort publishMemberEventPort;
    private final CreateNamespacePort createNamespacePort;

    @Transactional
    @Override
    public void signUpMember(SignUpMemberCommand signUpMemberCommand) {
        validateEmailNotDuplicated(signUpMemberCommand.getMemberEmail());
        validateEmailVerified(signUpMemberCommand.getMemberEmail());
        MemberPassword encodedPassword = encodePasswordPort.encodePassword(signUpMemberCommand.getMemberPassword());

        // Email is already confirmed via the pre-signup verify-email flow, so the member starts valid.
        Member member = Member.create(
                signUpMemberCommand.getMemberName(),
                signUpMemberCommand.getMemberEmail(),
                encodedPassword,
                new MemberRoles(List.of(Role.MEMBER)),
                new MemberIsValid(true)
        );
        Member savedMember = signUpMemberPort.createMember(member);
        // Outbox write in this transaction: commits or rolls back with the member row (#350). Lets
        // file-service claim pending guest shares invited to this email.
        publishMemberEventPort.publishSignedUp(savedMember.getId(), savedMember.getEmail());
        // The Feign call to file-service waits for the commit, so a signup that rolls back never gets
        // a namespace and the transaction doesn't hold its connection across an HTTP round trip (#208).
        AfterCommit.run(() -> createNamespacePort.createNamespace(savedMember.getId()));
    }

    private void validateEmailNotDuplicated(MemberEmail memberEmail) {
        if (checkEmailExistsPort.existsByEmail(memberEmail)) {
            throw new BusinessException(MemberExceptionCase.DUPLICATE_EMAIL);
        }
    }

    private void validateEmailVerified(MemberEmail memberEmail) {
        if (!emailVerificationTokenPort.consumeVerified(memberEmail.emailValue())) {
            throw new BusinessException(MemberExceptionCase.EMAIL_NOT_VERIFIED);
        }
    }

}
