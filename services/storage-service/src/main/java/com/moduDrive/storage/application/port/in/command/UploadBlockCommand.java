package com.moduDrive.storage.application.port.in.command;

import lombok.Getter;

import java.util.UUID;

@Getter
public class UploadBlockCommand {

    private final UUID userId;
    private final String hash;
    private final byte[] data;

    public UploadBlockCommand(UUID userId, String hash, byte[] data) {
        this.userId = userId;
        this.hash = hash;
        this.data = data;
    }
}
