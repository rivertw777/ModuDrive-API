package com.moduDrive.auth.application.port.in.command;

import com.moduDrive.common.core.validation.SelfValidating;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class RevokeMemberAccessCommand extends SelfValidating<RevokeMemberAccessCommand> {

    @NotBlank
    private final String memberId;

    public RevokeMemberAccessCommand(String memberId) {
        this.memberId = memberId;
        this.validateSelf();
    }
}
