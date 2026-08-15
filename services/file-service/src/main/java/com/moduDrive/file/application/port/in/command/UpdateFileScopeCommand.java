package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.domain.model.ShareScope;
import lombok.Getter;

import java.util.UUID;

@Getter
public class UpdateFileScopeCommand {

    private final FileId fileId;
    private final UUID callerId;
    private final ShareScope scope;
    /** Required when {@code scope == LINK}, ignored otherwise. */
    private final Role role;

    public UpdateFileScopeCommand(UUID fileId, UUID callerId, ShareScope scope, Role role) {
        this.fileId = new FileId(fileId);
        this.callerId = callerId;
        this.scope = scope;
        this.role = role;
    }
}
