package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileId;
import lombok.Getter;

import java.util.UUID;

@Getter
public class DeleteFileCommand {

    private final FileId fileId;
    private final UUID callerId;

    public DeleteFileCommand(UUID fileId, UUID callerId) {
        this.fileId = new FileId(fileId);
        this.callerId = callerId;
    }
}
