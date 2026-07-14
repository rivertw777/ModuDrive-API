package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import lombok.Getter;

@Getter
public class CreateNamespaceCommand {

    private final NamespaceUserId userId;

    public CreateNamespaceCommand(NamespaceUserId userId) {
        this.userId = userId;
    }
}
