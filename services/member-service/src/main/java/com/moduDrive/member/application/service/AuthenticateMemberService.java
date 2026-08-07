package com.moduDrive.member.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.member.application.port.in.command.AuthenticateMemberCommand;
import com.moduDrive.member.application.port.in.usecase.AuthenticateMemberUseCase;
import com.moduDrive.member.application.port.out.FindMemberPort;
import com.moduDrive.member.application.port.out.MatchesPasswordPort;
import com.moduDrive.member.exception.MemberExceptionCase;
import com.moduDrive.member.domain.model.Member;
import com.moduDrive.member.domain.model.Member.*;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
class AuthenticateMemberService implements AuthenticateMemberUseCase {

    private final FindMemberPort findMemberPort;
    private final MatchesPasswordPort matchesPasswordPort;

    @Override
    @Transactional(readOnly = true)
    public Member authenticateMember(AuthenticateMemberCommand authenticateMemberCommand) {
        Member member = findMemberPort.findMemberByEmail(
                authenticateMemberCommand.getMemberEmail()
        );

        validatePasswordMatches(
                authenticateMemberCommand.getMemberPassword(),
                new MemberPassword(member.getPassword())
        );

        return member;
    }

    private void validatePasswordMatches(MemberPassword rawPassword, MemberPassword encodedPassword) {
        if (!matchesPasswordPort.matchesPassword(rawPassword, encodedPassword)) {
            throw new BusinessException(MemberExceptionCase.PASSWORD_NOT_MATCHED);
        }
    }

}
