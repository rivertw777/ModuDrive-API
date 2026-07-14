package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import lombok.Getter;

@Getter
public class ListDirectoryCommand {

    private final NamespaceUserId userId;
    private final String path;

    public ListDirectoryCommand(Long userId, String path) {
        this.userId = new NamespaceUserId(userId);
        this.path = path;
    }
}
