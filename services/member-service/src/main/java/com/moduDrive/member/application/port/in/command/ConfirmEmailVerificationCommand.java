package com.moduDrive.member.application.port.in.command;

import com.moduDrive.common.core.validation.SelfValidating;
import com.moduDrive.member.domain.model.Member.MemberEmail;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class ConfirmEmailVerificationCommand extends SelfValidating<ConfirmEmailVerificationCommand> {

    @NotNull
    private final MemberEmail memberEmail;

    @NotNull
    private final String code;

    public ConfirmEmailVerificationCommand(MemberEmail memberEmail, String code) {
        this.memberEmail = memberEmail;
        this.code = code;
        this.validateSelf();
    }
}
