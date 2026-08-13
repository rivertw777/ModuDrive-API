package com.moduDrive.storage.application.port.in.command;

import lombok.Getter;

import java.util.UUID;

@Getter
public class SimpleUploadCommand {

    private final UUID fileId;
    private final UUID userId;
    private final byte[] data;
    private final long fileSize;

    public SimpleUploadCommand(String fileId, UUID userId, byte[] data) {
        this.fileId = UUID.fromString(fileId);
        this.userId = userId;
        this.data = data;
        this.fileSize = data.length;
    }
}
