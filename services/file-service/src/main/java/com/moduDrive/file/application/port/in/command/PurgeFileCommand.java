package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileId;
import lombok.Getter;

import java.util.UUID;

@Getter
public class PurgeFileCommand {

    private final FileId fileId;

    public PurgeFileCommand(UUID fileId) {
        this.fileId = new FileId(fileId);
    }
}
