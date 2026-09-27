package com.moduDrive.member.application.port.in.command;

import com.moduDrive.common.core.validation.SelfValidating;
import com.moduDrive.member.domain.model.Member.MemberId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class SendLoginVerificationMailCommand extends SelfValidating<SendLoginVerificationMailCommand> {

    @NotNull
    private final MemberId memberId;

    @NotBlank
    private final String code;

    public SendLoginVerificationMailCommand(MemberId memberId, String code) {
        this.memberId = memberId;
        this.code = code;
        this.validateSelf();
    }
}
