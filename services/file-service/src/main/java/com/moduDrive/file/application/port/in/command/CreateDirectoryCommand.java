package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileName;
import com.moduDrive.file.domain.model.File.FileOwnerId;
import com.moduDrive.file.domain.model.File.FilePath;
import lombok.Getter;

import java.util.UUID;

@Getter
public class CreateDirectoryCommand {

    private final UUID userId;
    private final FileName name;
    private final FilePath path;
    private final FileOwnerId ownerId;

    public CreateDirectoryCommand(UUID userId, String name, String path, UUID ownerId) {
        this.userId = userId;
        this.name = new FileName(name);
        this.path = new FilePath(path);
        this.ownerId = new FileOwnerId(ownerId);
    }
}
