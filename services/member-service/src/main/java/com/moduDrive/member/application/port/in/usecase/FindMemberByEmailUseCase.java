package com.moduDrive.member.application.port.in.usecase;

import com.moduDrive.member.application.port.in.command.FindMemberByEmailCommand;
import com.moduDrive.member.domain.model.Member;

public interface FindMemberByEmailUseCase {

    Member findMemberByEmail(FindMemberByEmailCommand findMemberByEmailCommand);
}
