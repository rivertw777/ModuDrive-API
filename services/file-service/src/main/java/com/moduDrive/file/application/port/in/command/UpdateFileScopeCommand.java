package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.ShareScope;
import lombok.Getter;

import java.util.UUID;

@Getter
public class UpdateFileScopeCommand {

    private final FileId fileId;
    private final UUID callerId;
    private final ShareScope scope;

    public UpdateFileScopeCommand(UUID fileId, UUID callerId, ShareScope scope) {
        this.fileId = new FileId(fileId);
        this.callerId = callerId;
        this.scope = scope;
    }
}
