package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileId;
import lombok.Getter;

import java.util.UUID;

@Getter
public class DeleteFileCommand {

    private final FileId fileId;

    public DeleteFileCommand(UUID fileId) {
        this.fileId = new FileId(fileId);
    }
}
