package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import lombok.Getter;

import java.util.UUID;

@Getter
public class EmptyTrashCommand {

    private final NamespaceUserId userId;

    public EmptyTrashCommand(UUID userId) {
        this.userId = new NamespaceUserId(userId);
    }
}
