package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileName;
import com.moduDrive.file.domain.model.File.FileOwnerId;
import com.moduDrive.file.domain.model.File.FilePath;
import lombok.Getter;

@Getter
public class CreateDirectoryCommand {

    private final Long userId;
    private final FileName name;
    private final FilePath path;
    private final FileOwnerId ownerId;

    public CreateDirectoryCommand(Long userId, String name, String path, Long ownerId) {
        this.userId = userId;
        this.name = new FileName(name);
        this.path = new FilePath(path);
        this.ownerId = new FileOwnerId(ownerId);
    }
}
