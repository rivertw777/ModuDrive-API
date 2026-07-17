package com.moduDrive.storage.application.port.in.command;

import com.moduDrive.common.core.validation.SelfValidating;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class CompleteResumableUploadCommand extends SelfValidating<CompleteResumableUploadCommand> {

    @NotNull
    private final UUID sessionId;
    private final long userId;

    public CompleteResumableUploadCommand(String sessionId, long userId) {
        this.sessionId = UUID.fromString(sessionId);
        this.userId = userId;
        this.validateSelf();
    }
}
