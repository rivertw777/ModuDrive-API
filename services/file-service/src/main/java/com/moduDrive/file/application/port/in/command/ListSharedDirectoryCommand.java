package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileId;
import lombok.Getter;

import java.util.UUID;

@Getter
public class ListSharedDirectoryCommand {

    private final FileId directoryId;
    private final UUID callerId;

    public ListSharedDirectoryCommand(UUID directoryId, UUID callerId) {
        this.directoryId = new FileId(directoryId);
        this.callerId = callerId;
    }
}
