package com.moduDrive.storage.application.port.in.command;

import com.moduDrive.common.core.validation.SelfValidating;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class DownloadFileCommand extends SelfValidating<DownloadFileCommand> {

    @NotNull
    private final UUID fileId;

    @NotNull
    private final UUID userId;

    public DownloadFileCommand(String fileId, UUID userId) {
        this.fileId = UUID.fromString(fileId);
        this.userId = userId;
        this.validateSelf();
    }
}
