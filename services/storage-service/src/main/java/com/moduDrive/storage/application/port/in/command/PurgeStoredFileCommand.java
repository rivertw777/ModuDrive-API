package com.moduDrive.storage.application.port.in.command;

import lombok.Getter;

import java.util.UUID;

@Getter
public class PurgeStoredFileCommand {

    private final UUID fileId;
    private final UUID userId;

    public PurgeStoredFileCommand(UUID fileId, UUID userId) {
        this.fileId = fileId;
        this.userId = userId;
    }
}
