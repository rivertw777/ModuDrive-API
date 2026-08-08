package com.moduDrive.storage.application.port.in.command;

import com.moduDrive.common.core.validation.SelfValidating;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class InitResumableUploadCommand extends SelfValidating<InitResumableUploadCommand> {

    @NotNull
    private final UUID fileId;
    private final UUID userId;
    @Positive
    private final int totalChunks;
    @Positive
    private final long fileSize;

    public InitResumableUploadCommand(String fileId, UUID userId, int totalChunks, long fileSize) {
        this.fileId = UUID.fromString(fileId);
        this.userId = userId;
        this.totalChunks = totalChunks;
        this.fileSize = fileSize;
        this.validateSelf();
    }
}
