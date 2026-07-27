package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.File.FileName;
import lombok.Getter;

import java.util.UUID;

@Getter
public class RenameFileCommand {

    private final FileId fileId;
    private final FileName name;

    public RenameFileCommand(UUID fileId, String name) {
        this.fileId = new FileId(fileId);
        this.name = new FileName(name);
    }
}
