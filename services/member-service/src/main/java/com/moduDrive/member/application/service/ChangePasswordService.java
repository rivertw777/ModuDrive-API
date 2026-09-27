package com.moduDrive.member.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.member.application.port.in.command.ChangePasswordCommand;
import com.moduDrive.member.application.port.in.usecase.ChangePasswordUseCase;
import com.moduDrive.member.application.port.out.ChangePasswordPort;
import com.moduDrive.member.application.port.out.EncodePasswordPort;
import com.moduDrive.member.application.port.out.FindMemberPort;
import com.moduDrive.member.application.port.out.MatchesPasswordPort;
import com.moduDrive.member.application.port.out.PublishMemberEventPort;
import com.moduDrive.member.domain.model.Member;
import com.moduDrive.member.domain.model.Member.MemberPassword;
import com.moduDrive.member.exception.MemberExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asks for the current password even though the caller is signed in — a stolen session alone can't
 * lock the member out. Every session and verified device of the member ends with it (auth spec 004).
 */
@UseCase
@RequiredArgsConstructor
class ChangePasswordService implements ChangePasswordUseCase {

    private final FindMemberPort findMemberPort;
    private final MatchesPasswordPort matchesPasswordPort;
    private final EncodePasswordPort encodePasswordPort;
    private final ChangePasswordPort changePasswordPort;
    private final PublishMemberEventPort publishMemberEventPort;

    @Transactional
    @Override
    public void changePassword(ChangePasswordCommand command) {
        Member member = findMemberPort.findMemberById(command.getMemberId());
        if (!matchesPasswordPort.matchesPassword(command.getCurrentPassword(), new MemberPassword(member.getPassword()))) {
            throw new BusinessException(MemberExceptionCase.WRONG_CURRENT_PASSWORD);
        }
        changePasswordPort.changePassword(command.getMemberId(), encodePasswordPort.encodePassword(command.getNewPassword()));
        // Same transaction as the new password: auth-service can't miss it, and never hears of a
        // change that rolled back.
        publishMemberEventPort.publishPasswordChanged(member.getId());
    }
}
