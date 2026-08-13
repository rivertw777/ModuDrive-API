package com.moduDrive.member.application.port.in.command;

import com.moduDrive.common.core.validation.SelfValidating;
import com.moduDrive.member.domain.model.Member.MemberEmail;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class FindMemberByEmailCommand extends SelfValidating<FindMemberByEmailCommand> {

    @NotNull
    private final MemberEmail memberEmail;

    public FindMemberByEmailCommand(MemberEmail memberEmail) {
        this.memberEmail = memberEmail;
        this.validateSelf();
    }
}
