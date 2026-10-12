package com.moduDrive.file.application.port.in.command;

import lombok.Getter;

/** Raw Strings for the same reason as {@link GetPublicFileCommand}. */
@Getter
public class GetPublicFileRevisionsCommand {

    private final String fileId;
    private final String key;
    private final int limit;

    public GetPublicFileRevisionsCommand(String fileId, String key, int limit) {
        this.fileId = fileId;
        this.key = key;
        this.limit = limit;
    }
}
