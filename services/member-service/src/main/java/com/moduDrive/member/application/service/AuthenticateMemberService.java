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

/**
 * An unknown email and a wrong password fail the same way — same code, same message, and (by
 * checking against a dummy hash) about the same time — so a login attempt never tells whether an
 * email is registered (#445).
 */
@UseCase
@RequiredArgsConstructor
class AuthenticateMemberService implements AuthenticateMemberUseCase {

    /** A BCrypt hash at the encoder's strength (10), checked when the email is unknown so that
     * path costs the same as a real password check. Regenerate it if the strength changes. */
    private static final MemberPassword DUMMY_PASSWORD_HASH =
            new MemberPassword("$2a$10$igHpKm23MFmTG2nvSQVkduB2cqy/PMJh9l/bSi6Dwb2cH6O8RhnWu");

    private final FindMemberPort findMemberPort;
    private final MatchesPasswordPort matchesPasswordPort;

    @Override
    @Transactional(readOnly = true)
    public Member authenticateMember(AuthenticateMemberCommand authenticateMemberCommand) {
        MemberPassword rawPassword = authenticateMemberCommand.getMemberPassword();
        Member member;
        try {
            member = findMemberPort.findMemberByEmail(authenticateMemberCommand.getMemberEmail());
        } catch (BusinessException e) {
            if (e.getExceptionCase() != MemberExceptionCase.MEMBER_NOT_FOUND) {
                throw e;
            }
            matchesPasswordPort.matchesPassword(rawPassword, DUMMY_PASSWORD_HASH);
            throw new BusinessException(MemberExceptionCase.INVALID_CREDENTIALS);
        }

        if (!matchesPasswordPort.matchesPassword(rawPassword, new MemberPassword(member.getPassword()))) {
            throw new BusinessException(MemberExceptionCase.INVALID_CREDENTIALS);
        }
        return member;
    }

}
