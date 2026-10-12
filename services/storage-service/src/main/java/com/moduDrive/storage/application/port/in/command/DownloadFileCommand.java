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

    /** True only for {@code viewFile}: applies the inline preview size cap and marks the file as
     * recently accessed (a Drive-style "open"); a plain download does neither. */
    private final boolean inlinePreview;

    public DownloadFileCommand(String fileId, UUID userId) {
        this(fileId, userId, false);
    }

    public DownloadFileCommand(String fileId, UUID userId, boolean inlinePreview) {
        this.fileId = UUID.fromString(fileId);
        this.userId = userId;
        this.inlinePreview = inlinePreview;
        this.validateSelf();
    }
}
