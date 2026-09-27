package com.moduDrive.member.application.port.in.command;

import com.moduDrive.common.core.validation.SelfValidating;
import com.moduDrive.member.domain.model.Member.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@EqualsAndHashCode(callSuper = false)
public class ChangePasswordCommand extends SelfValidating<ChangePasswordCommand> {

    @NotNull
    private final MemberId memberId;

    @NotNull
    private final MemberPassword currentPassword;

    @NotNull
    private final MemberPassword newPassword;

    public ChangePasswordCommand(MemberId memberId, MemberPassword currentPassword, MemberPassword newPassword) {
        this.memberId = memberId;
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
        this.validateSelf();
    }
}
