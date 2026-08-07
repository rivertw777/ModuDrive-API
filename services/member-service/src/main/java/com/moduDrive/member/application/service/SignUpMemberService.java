package com.moduDrive.member.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.member.application.port.in.command.SignUpMemberCommand;
import com.moduDrive.member.application.port.in.usecase.SignUpMemberUseCase;
import com.moduDrive.member.application.port.out.CheckEmailExistsPort;
import com.moduDrive.member.application.port.out.CreateNamespacePort;
import com.moduDrive.member.application.port.out.EncodePasswordPort;
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
    private final CreateNamespacePort createNamespacePort;

    @Transactional
    @Override
    public void signUpMember(SignUpMemberCommand signUpMemberCommand) {
        validateEmailNotDuplicated(signUpMemberCommand.getMemberEmail());
        MemberPassword encodedPassword = encodePasswordPort.encodePassword(signUpMemberCommand.getMemberPassword());

        Member member = Member.create(
                signUpMemberCommand.getMemberName(),
                signUpMemberCommand.getMemberEmail(),
                encodedPassword,
                new MemberRoles(List.of(Role.MEMBER)),
                new MemberIsValid(true)
        );
        Member savedMember = signUpMemberPort.createMember(member);
        createNamespacePort.createNamespace(savedMember.getId());
    }

    private void validateEmailNotDuplicated(MemberEmail memberEmail) {
        if (checkEmailExistsPort.existsByEmail(memberEmail)) {
            throw new BusinessException(MemberExceptionCase.DUPLICATE_EMAIL);
        }
    }

}
