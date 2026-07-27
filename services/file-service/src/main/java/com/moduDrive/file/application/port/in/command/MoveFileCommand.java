package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.File.FilePath;
import lombok.Getter;

import java.util.UUID;

@Getter
public class MoveFileCommand {

    private final FileId fileId;
    private final FilePath path;

    public MoveFileCommand(UUID fileId, String path) {
        this.fileId = new FileId(fileId);
        this.path = new FilePath(path);
    }
}
