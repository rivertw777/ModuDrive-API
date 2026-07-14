package com.moduDrive.storage.application.port.in.command;

import lombok.Getter;

import java.util.UUID;

@Getter
public class SimpleUploadCommand {

    private final UUID fileId;
    private final byte[] data;
    private final long fileSize;

    public SimpleUploadCommand(String fileId, byte[] data) {
        this.fileId = UUID.fromString(fileId);
        this.data = data;
        this.fileSize = data.length;
    }
}
