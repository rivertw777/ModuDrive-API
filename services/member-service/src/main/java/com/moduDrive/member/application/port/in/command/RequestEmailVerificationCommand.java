package com.moduDrive.member.application.port.in.command;

import com.moduDrive.common.core.validation.SelfValidating;
import com.moduDrive.member.domain.model.Member.MemberEmail;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class RequestEmailVerificationCommand extends SelfValidating<RequestEmailVerificationCommand> {

    @NotNull
    private final MemberEmail memberEmail;

    public RequestEmailVerificationCommand(MemberEmail memberEmail) {
        this.memberEmail = memberEmail;
        this.validateSelf();
    }
}
