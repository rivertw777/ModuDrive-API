package com.moduDrive.file.application.port.in.command;

import lombok.Getter;

import java.util.UUID;

/** A folder of an uploaded folder tree that no file commit creates (spec 001 2-2). {@code path} is
 * the folder above it; the service validates both, since they become rows. */
@Getter
public class CommitDirectoryCommand {

    private final UUID callerId;
    private final String path;
    private final String name;

    public CommitDirectoryCommand(UUID callerId, String path, String name) {
        this.callerId = callerId;
        this.path = path;
        this.name = name;
    }
}
