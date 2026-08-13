package com.moduDrive.member.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.member.application.port.in.command.FindMemberByEmailCommand;
import com.moduDrive.member.application.port.in.usecase.FindMemberByEmailUseCase;
import com.moduDrive.member.application.port.out.FindMemberPort;
import com.moduDrive.member.domain.model.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
class FindMemberByEmailService implements FindMemberByEmailUseCase {

    private final FindMemberPort findMemberPort;

    @Transactional(readOnly = true)
    @Override
    public Member findMemberByEmail(FindMemberByEmailCommand findMemberByEmailCommand) {
        return findMemberPort.findMemberByEmail(
                findMemberByEmailCommand.getMemberEmail()
        );
    }
}
