package com.moduDrive.storage.application.port.in.command;

import com.moduDrive.common.core.validation.SelfValidating;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class UploadChunkCommand extends SelfValidating<UploadChunkCommand> {

    @NotNull
    private final UUID sessionId;
    private final long userId;
    @Min(0)
    private final int chunkIndex;
    @NotNull
    private final byte[] data;

    public UploadChunkCommand(String sessionId, long userId, int chunkIndex, byte[] data) {
        this.sessionId = UUID.fromString(sessionId);
        this.userId = userId;
        this.chunkIndex = chunkIndex;
        this.data = data;
        this.validateSelf();
    }
}
