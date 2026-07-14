package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FilePath;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import lombok.Getter;

@Getter
public class ListDirectoryCommand {

    private final NamespaceUserId userId;
    private final FilePath path;

    public ListDirectoryCommand(Long userId, String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        this.userId = new NamespaceUserId(userId);
        this.path = new FilePath(path);
    }
}
