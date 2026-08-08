package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import lombok.Getter;

import java.util.UUID;

@Getter
public class ListAllFilesCommand {

    private final NamespaceUserId userId;

    public ListAllFilesCommand(UUID userId) {
        this.userId = new NamespaceUserId(userId);
    }
}
